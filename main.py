import streamlit as st
import pandas as pd
import io
import zipfile

# ─────────────────────────────────────────────
#  PAGE CONFIG
# ─────────────────────────────────────────────
st.set_page_config(
    page_title="DKL Migration Validator",
    page_icon="🔍",
    layout="wide",
)

st.title("🔍 DKL API — Legal Entity & Country Migration Validator")
st.markdown(
    "Upload the **5 required files** below. The tool will classify every dataset into "
    "one of four scenarios and produce downloadable CSV reports."
)

# ─────────────────────────────────────────────
#  CONSTANTS
# ─────────────────────────────────────────────
REQUIRED_FILES = {
    "dataset_legal_entity":  {
        "label": "📂 dataset_legal_entity_dkl_api.csv",
        "required_cols": {"id_entity", "id_legal_entity"},
        "key": "f_dataset_le",
    },
    "dataset_country": {
        "label": "📂 dataset_country_dkl_api.csv",
        "required_cols": {"id_entity", "code_iso_2a"},
        "key": "f_dataset_country",
    },
    "legal_entity_referential": {
        "label": "📂 legal_entity_referential.csv",
        "required_cols": {"id_legal_entity", "elr_code", "full_name", "id_country"},
        "key": "f_le_ref",
    },
    "country_referential": {
        "label": "📂 country_referential.csv",
        "required_cols": {"id_country", "code_iso_2a", "label_en"},
        "key": "f_country_ref",
    },
    "country_dkl_api": {
        "label": "📂 country_dkl_api.csv",
        "required_cols": {"code_iso_2a", "label_en"},
        "key": "f_country_dkl",
    },
}

OUTPUT_COLS = [
    "id_entity",
    "id_legal_entity",
    "legal_entity_name",
    "elr_code",
    "country_iso",
    "country_label_en",
    "scenario_note",
]

# ─────────────────────────────────────────────
#  HELPERS
# ─────────────────────────────────────────────

def load_csv(uploaded_file) -> pd.DataFrame:
    """Read an uploaded file into a DataFrame."""
    return pd.read_csv(uploaded_file)


def validate_columns(df: pd.DataFrame, required: set, filename: str):
    missing = required - set(df.columns)
    if missing:
        st.error(f"❌ **{filename}** is missing columns: `{missing}`")
        return False
    return True


def df_to_csv_bytes(df: pd.DataFrame) -> bytes:
    buf = io.StringIO()
    df.to_csv(buf, index=False)
    return buf.getvalue().encode("utf-8")


def build_zip(scenario_dfs: dict) -> bytes:
    """Pack all 4 scenario CSVs into a single ZIP."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        for name, df in scenario_dfs.items():
            zf.writestr(f"{name}.csv", df_to_csv_bytes(df))
    return buf.getvalue()


# ─────────────────────────────────────────────
#  FILE UPLOAD SECTION
# ─────────────────────────────────────────────
st.markdown("---")
st.subheader("📤 Step 1 — Upload the 5 source files")

cols = st.columns(5)
uploaded = {}
for idx, (name, meta) in enumerate(REQUIRED_FILES.items()):
    with cols[idx]:
        uploaded[name] = st.file_uploader(
            meta["label"],
            type=["csv"],
            key=meta["key"],
            help=f"Required columns: {', '.join(sorted(meta['required_cols']))}",
        )

# ─────────────────────────────────────────────
#  RUN ANALYSIS
# ─────────────────────────────────────────────
st.markdown("---")

all_uploaded = all(v is not None for v in uploaded.values())

if not all_uploaded:
    missing_names = [REQUIRED_FILES[k]["label"] for k, v in uploaded.items() if v is None]
    st.info(f"⏳ Waiting for: {', '.join(missing_names)}")
    st.stop()

run_btn = st.button("🚀 Run Analysis", type="primary", use_container_width=True)
if not run_btn:
    st.stop()

# ── Load DataFrames ──────────────────────────
with st.spinner("Loading files…"):
    dfs = {}
    valid = True
    for name, meta in REQUIRED_FILES.items():
        df = load_csv(uploaded[name])
        if not validate_columns(df, meta["required_cols"], meta["label"]):
            valid = False
        dfs[name] = df

if not valid:
    st.stop()

dataset_le_raw   = dfs["dataset_legal_entity"].copy()
dataset_country  = dfs["dataset_country"].copy()
le_ref           = dfs["legal_entity_referential"].copy()
country_ref      = dfs["country_referential"].copy()
country_dkl      = dfs["country_dkl_api"].copy()

# ── Normalise types ──────────────────────────
dataset_le_raw["id_entity"]       = dataset_le_raw["id_entity"].astype(str).str.strip()
dataset_le_raw["id_legal_entity"] = pd.to_numeric(dataset_le_raw["id_legal_entity"], errors="coerce")
dataset_country["id_entity"]      = dataset_country["id_entity"].astype(str).str.strip()
dataset_country["code_iso_2a"]    = dataset_country["code_iso_2a"].astype(str).str.strip().str.upper()
le_ref["id_legal_entity"]         = pd.to_numeric(le_ref["id_legal_entity"], errors="coerce")
le_ref["id_country"]              = pd.to_numeric(le_ref["id_country"], errors="coerce")
country_ref["id_country"]         = pd.to_numeric(country_ref["id_country"], errors="coerce")
country_ref["code_iso_2a"]        = country_ref["code_iso_2a"].astype(str).str.strip().str.upper()

# ── Remove placeholder LE rows (id_legal_entity == 0) ──
dataset_le = dataset_le_raw[dataset_le_raw["id_legal_entity"].notna() & (dataset_le_raw["id_legal_entity"] != 0)].copy()

# ── Build enriched LE lookup: id_legal_entity → name, elr_code, expected country ──
le_enriched = le_ref.merge(
    country_ref[["id_country", "code_iso_2a", "label_en"]].rename(
        columns={"code_iso_2a": "expected_iso", "label_en": "expected_country_label"}
    ),
    on="id_country",
    how="left",
)[["id_legal_entity", "elr_code", "full_name", "expected_iso", "expected_country_label"]]

# ── Build country label lookup from DKL API country table ──
country_label_map = (
    country_dkl[["code_iso_2a", "label_en"]]
    .drop_duplicates("code_iso_2a")
    .set_index("code_iso_2a")["label_en"]
    .to_dict()
)
# Supplement with country_ref labels
for _, row in country_ref.iterrows():
    if row["code_iso_2a"] not in country_label_map:
        country_label_map[row["code_iso_2a"]] = row["label_en"]

# ── Entity sets ──────────────────────────────
entities_with_le      = set(dataset_le["id_entity"].unique())
entities_with_country = set(dataset_country["id_entity"].unique())

entities_both         = entities_with_le & entities_with_country
entities_le_only      = entities_with_le - entities_with_country       # Scenario 3
entities_country_only = entities_with_country - entities_with_le       # Scenario 4

# ─────────────────────────────────────────────
#  CORE CLASSIFICATION (entities_both → S1 / S2)
# ─────────────────────────────────────────────

s1_rows = []  # valid
s2_rows = []  # mismatch

progress = st.progress(0, text="Classifying datasets…")
entities_both_list = sorted(entities_both)
total = len(entities_both_list)

for i, entity_id in enumerate(entities_both_list):
    if i % 200 == 0:
        progress.progress(int(i / total * 100), text=f"Classifying datasets… {i}/{total}")

    # Legal entities for this dataset
    le_ids = dataset_le[dataset_le["id_entity"] == entity_id]["id_legal_entity"].unique()
    # Countries for this dataset
    actual_isos = set(
        dataset_country[dataset_country["id_entity"] == entity_id]["code_iso_2a"].unique()
    )

    # Enrich each LE with its expected country
    le_detail = le_enriched[le_enriched["id_legal_entity"].isin(le_ids)]

    # Expected ISO set (from referential, ignoring NaN)
    expected_isos = set(le_detail["expected_iso"].dropna().unique())

    # For each actual country in the dataset, decide valid / mismatch
    for iso in actual_isos:
        label = country_label_map.get(iso, "")
        is_valid = iso in expected_isos

        # For each LE associated to this entity, produce a row
        if le_detail.empty:
            # LE exists in dataset_le but not in referential
            row = {
                "id_entity": entity_id,
                "id_legal_entity": None,
                "legal_entity_name": "NOT FOUND IN REFERENTIAL",
                "elr_code": None,
                "country_iso": iso,
                "country_label_en": label,
            }
            if is_valid:
                row["scenario_note"] = "Valid"
                s1_rows.append(row)
            else:
                row["scenario_note"] = "Country not in referential mapping"
                s2_rows.append(row)
        else:
            for _, le_row in le_detail.iterrows():
                row = {
                    "id_entity": entity_id,
                    "id_legal_entity": int(le_row["id_legal_entity"]),
                    "legal_entity_name": le_row["full_name"],
                    "elr_code": le_row["elr_code"],
                    "country_iso": iso,
                    "country_label_en": label,
                }
                if is_valid:
                    row["scenario_note"] = "Valid"
                    s1_rows.append(row)
                else:
                    row["scenario_note"] = f"Country {iso} not mapped to any LE in referential"
                    s2_rows.append(row)

progress.progress(100, text="Classification complete.")

# ─────────────────────────────────────────────
#  SCENARIO 3 — LE but NO country
# ─────────────────────────────────────────────
s3_rows = []
for entity_id in entities_le_only:
    le_ids = dataset_le[dataset_le["id_entity"] == entity_id]["id_legal_entity"].unique()
    le_detail = le_enriched[le_enriched["id_legal_entity"].isin(le_ids)]

    if le_detail.empty:
        s3_rows.append({
            "id_entity": entity_id,
            "id_legal_entity": None,
            "legal_entity_name": "NOT FOUND IN REFERENTIAL",
            "elr_code": None,
            "country_iso": None,
            "country_label_en": None,
            "scenario_note": "Has LE, no country assigned",
        })
    else:
        for _, le_row in le_detail.iterrows():
            s3_rows.append({
                "id_entity": entity_id,
                "id_legal_entity": int(le_row["id_legal_entity"]),
                "legal_entity_name": le_row["full_name"],
                "elr_code": le_row["elr_code"],
                "country_iso": None,
                "country_label_en": None,
                "scenario_note": "Has LE, no country assigned",
            })

# ─────────────────────────────────────────────
#  SCENARIO 4 — Country but NO LE
# ─────────────────────────────────────────────
s4_rows = []
for entity_id in entities_country_only:
    actual_isos = dataset_country[dataset_country["id_entity"] == entity_id]["code_iso_2a"].unique()
    for iso in actual_isos:
        label = country_label_map.get(iso, "")
        s4_rows.append({
            "id_entity": entity_id,
            "id_legal_entity": None,
            "legal_entity_name": None,
            "elr_code": None,
            "country_iso": iso,
            "country_label_en": label,
            "scenario_note": "Has country, no LE assigned",
        })

# ─────────────────────────────────────────────
#  BUILD DataFrames — deduplicate S1
# ─────────────────────────────────────────────
def make_df(rows):
    if not rows:
        return pd.DataFrame(columns=OUTPUT_COLS)
    df = pd.DataFrame(rows)[OUTPUT_COLS]
    return df.drop_duplicates().reset_index(drop=True)

# Scenario 1: only entities where ALL their countries are valid (no mismatch)
# First find entity_ids that appear in s2
s2_entity_ids = set(r["id_entity"] for r in s2_rows)
# Keep only rows where entity is NOT in s2
s1_rows_clean = [r for r in s1_rows if r["id_entity"] not in s2_entity_ids]

df_s1 = make_df(s1_rows_clean)
df_s2 = make_df(s2_rows)
df_s3 = make_df(s3_rows)
df_s4 = make_df(s4_rows)

scenario_dfs = {
    "scenario1_valid": df_s1,
    "scenario2_country_mismatch": df_s2,
    "scenario3_le_no_country": df_s3,
    "scenario4_country_no_le": df_s4,
}

# ─────────────────────────────────────────────
#  SUMMARY METRICS
# ─────────────────────────────────────────────
st.markdown("---")
st.subheader("📊 Step 2 — Summary")

m1, m2, m3, m4 = st.columns(4)
m1.metric("✅ Scenario 1 — Valid",          f"{df_s1['id_entity'].nunique():,} datasets  ({len(df_s1):,} rows)")
m2.metric("⚠️  Scenario 2 — Country Mismatch", f"{df_s2['id_entity'].nunique():,} datasets  ({len(df_s2):,} rows)")
m3.metric("🔴 Scenario 3 — LE, No Country",  f"{df_s3['id_entity'].nunique():,} datasets  ({len(df_s3):,} rows)")
m4.metric("🔵 Scenario 4 — Country, No LE",  f"{df_s4['id_entity'].nunique():,} datasets  ({len(df_s4):,} rows)")

# ─────────────────────────────────────────────
#  PREVIEW TABS
# ─────────────────────────────────────────────
st.markdown("---")
st.subheader("🔎 Step 3 — Preview Results")

tab1, tab2, tab3, tab4 = st.tabs([
    "✅ Scenario 1 — Valid",
    "⚠️ Scenario 2 — Mismatch",
    "🔴 Scenario 3 — LE, No Country",
    "🔵 Scenario 4 — Country, No LE",
])

SCENARIO_DESCRIPTIONS = {
    "tab1": (
        "**Scenario 1 — All Valid:** Datasets where every country assigned matches the "
        "expected country from the legal entity referential. These are safe — no migration action needed."
    ),
    "tab2": (
        "**Scenario 2 — Country Mismatch:** Datasets that have both a legal entity and a country, "
        "but at least one country does NOT match what the referential expects for that legal entity. "
        "Only the **invalid/mismatched** country rows are shown here."
    ),
    "tab3": (
        "**Scenario 3 — Legal Entity, No Country:** Datasets that have a legal entity linked "
        "but zero countries assigned. Country needs to be populated during migration."
    ),
    "tab4": (
        "**Scenario 4 — Country, No Legal Entity:** Datasets that have a country assigned "
        "but no legal entity linked. Legal entity needs to be populated during migration."
    ),
}

def show_tab(tab, df, desc):
    with tab:
        st.markdown(desc)
        st.dataframe(df, use_container_width=True, height=400)
        st.caption(f"{len(df):,} rows | {df['id_entity'].nunique():,} unique datasets")

show_tab(tab1, df_s1, SCENARIO_DESCRIPTIONS["tab1"])
show_tab(tab2, df_s2, SCENARIO_DESCRIPTIONS["tab2"])
show_tab(tab3, df_s3, SCENARIO_DESCRIPTIONS["tab3"])
show_tab(tab4, df_s4, SCENARIO_DESCRIPTIONS["tab4"])

# ─────────────────────────────────────────────
#  DOWNLOADS
# ─────────────────────────────────────────────
st.markdown("---")
st.subheader("⬇️ Step 4 — Download Reports")

col_dl1, col_dl2, col_dl3, col_dl4, col_dl_all = st.columns(5)

with col_dl1:
    st.download_button(
        label="✅ Scenario 1\n(Valid)",
        data=df_to_csv_bytes(df_s1),
        file_name="scenario1_valid.csv",
        mime="text/csv",
        use_container_width=True,
    )

with col_dl2:
    st.download_button(
        label="⚠️ Scenario 2\n(Mismatch)",
        data=df_to_csv_bytes(df_s2),
        file_name="scenario2_country_mismatch.csv",
        mime="text/csv",
        use_container_width=True,
    )

with col_dl3:
    st.download_button(
        label="🔴 Scenario 3\n(LE, No Country)",
        data=df_to_csv_bytes(df_s3),
        file_name="scenario3_le_no_country.csv",
        mime="text/csv",
        use_container_width=True,
    )

with col_dl4:
    st.download_button(
        label="🔵 Scenario 4\n(Country, No LE)",
        data=df_to_csv_bytes(df_s4),
        file_name="scenario4_country_no_le.csv",
        mime="text/csv",
        use_container_width=True,
    )

with col_dl_all:
    st.download_button(
        label="📦 Download All\n(ZIP)",
        data=build_zip(scenario_dfs),
        file_name="dkl_migration_report.zip",
        mime="application/zip",
        use_container_width=True,
    )

st.markdown("---")
st.caption(
    "DKL Migration Validator · Logic: datasets classified by comparing "
    "dataset_legal_entity + dataset_country against legal_entity_referential + country_referential."
)
