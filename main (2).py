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
    "Upload the **12 required files** below. The tool classifies every dataset into "
    "one of four scenarios and produces downloadable CSV reports enriched with "
    "owner, manager, and their backup details."
)

# ─────────────────────────────────────────────
#  FILE DEFINITIONS
# ─────────────────────────────────────────────

# ── Group 1: existing 5 core files ───────────
CORE_FILES = {
    "dataset_legal_entity": {
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

# ── Group 2: new 7 person/org files ──────────
PERSON_FILES = {
    "person": {
        "label": "📂 person_*.csv",
        "required_cols": {"id", "first_name", "last_name", "email"},
        "key": "f_person",
    },
    "entity_person_owner": {
        "label": "📂 entity_person_owner_*.csv",
        "required_cols": {"id_entity", "person_id"},
        "key": "f_owner",
    },
    "entity_person_owner_backup": {
        "label": "📂 entity_person_owner_backup_*.csv",
        "required_cols": {"entity_id", "person_id"},
        "key": "f_owner_backup",
    },
    "entity_person_manager": {
        "label": "📂 entity_person_manager_*.csv",
        "required_cols": {"id_entity", "person_id"},
        "key": "f_manager",
    },
    "entity_person_manager_backup": {
        "label": "📂 entity_person_manager_backup_*.csv",
        "required_cols": {"id_entity", "person_id"},
        "key": "f_manager_backup",
    },
    "entity_bu_su": {
        "label": "📂 entity_bu_su_*.csv",
        "required_cols": {"entity_id", "bu_su_id"},
        "key": "f_bu_su",
    },
    "organizational_unit": {
        "label": "📂 organizational_unit_*.csv",
        "required_cols": {"id", "name"},
        "key": "f_org_unit",
    },
}

ALL_FILES = {**CORE_FILES, **PERSON_FILES}

# ── Output columns (scenario + person columns) ──
OUTPUT_COLS = [
    "id_entity",
    # person columns injected here
    "owner_name",
    "owner_email",
    "owner_backup_name",
    "owner_backup_email",
    "manager_name",
    "manager_email",
    "manager_backup_name",
    "manager_backup_email",
    # LE / country columns
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
    try:
        return pd.read_csv(uploaded_file, encoding="utf-8")
    except UnicodeDecodeError:
        uploaded_file.seek(0)
        return pd.read_csv(uploaded_file, encoding="latin-1")


def validate_columns(df: pd.DataFrame, required: set, filename: str) -> bool:
    missing = required - set(df.columns)
    if missing:
        st.error(f"❌ **{filename}** is missing columns: `{missing}`")
        return False
    return True


def df_to_csv_bytes(df: pd.DataFrame) -> bytes:
    """UTF-8 BOM so Excel renders accented chars (SOCIÉTÉ GÉNÉRALE) correctly."""
    buf = io.StringIO()
    df.to_csv(buf, index=False, encoding="utf-8-sig")
    return buf.getvalue().encode("utf-8-sig")


def build_zip(scenario_dfs: dict) -> bytes:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        for name, df in scenario_dfs.items():
            zf.writestr(f"{name}.csv", df_to_csv_bytes(df))
    return buf.getvalue()


def format_iso_set(iso_set: set, label_map: dict) -> str:
    if not iso_set:
        return "None"
    return ", ".join(sorted(f"{iso} ({label_map.get(iso, '?')})" for iso in iso_set))


def join_persons(person_ids: list, person_lookup: dict) -> tuple[str, str]:
    """
    Given a list of person_ids return two semicolon-separated strings:
    (full_name_combined, emails).
    Multiple people → 'LAST1 FIRST1; LAST2 FIRST2', 'email1; email2'
    """
    names, emails = [], []
    for pid in person_ids:
        p = person_lookup.get(int(pid) if pd.notna(pid) else None)
        if p:
            names.append(f"{p['last_name']} {p['first_name']}")
            emails.append(p["email"] if pd.notna(p.get("email")) else "")
    return ("; ".join(names) if names else "", "; ".join(emails) if emails else "")


def build_person_lookup(person_df: pd.DataFrame) -> dict:
    """id → {first_name, last_name, email}"""
    lookup = {}
    for _, row in person_df.iterrows():
        lookup[int(row["id"])] = {
            "first_name": str(row["first_name"]).strip() if pd.notna(row["first_name"]) else "",
            "last_name":  str(row["last_name"]).strip()  if pd.notna(row["last_name"])  else "",
            "email":      str(row["email"]).strip()       if pd.notna(row["email"])       else "",
        }
    return lookup


def build_entity_person_map(df: pd.DataFrame, entity_col: str) -> dict:
    """entity_id → [person_id, ...]"""
    result = {}
    for _, row in df.iterrows():
        eid = str(row[entity_col]).strip()
        pid = row["person_id"]
        result.setdefault(eid, []).append(pid)
    return result


def get_person_cols(entity_id: str, owner_map, owner_backup_map,
                    manager_map, manager_backup_map, person_lookup) -> dict:
    """Return dict of the 8 owner/manager columns for a given entity_id."""
    owner_name,          owner_email          = join_persons(owner_map.get(entity_id, []),          person_lookup)
    owner_backup_name,   owner_backup_email   = join_persons(owner_backup_map.get(entity_id, []),   person_lookup)
    manager_name,        manager_email        = join_persons(manager_map.get(entity_id, []),         person_lookup)
    manager_backup_name, manager_backup_email = join_persons(manager_backup_map.get(entity_id, []), person_lookup)
    return {
        "owner_name":          owner_name,
        "owner_email":         owner_email,
        "owner_backup_name":   owner_backup_name,
        "owner_backup_email":  owner_backup_email,
        "manager_name":        manager_name,
        "manager_email":       manager_email,
        "manager_backup_name": manager_backup_name,
        "manager_backup_email":manager_backup_email,
    }


# ─────────────────────────────────────────────
#  FILE UPLOAD — TWO GROUPS
# ─────────────────────────────────────────────
st.markdown("---")
st.subheader("📤 Step 1 — Upload source files")

st.markdown("**Group A — DKL Core Files (5 files)**")
core_cols = st.columns(5)
uploaded = {}
for idx, (name, meta) in enumerate(CORE_FILES.items()):
    with core_cols[idx]:
        uploaded[name] = st.file_uploader(
            meta["label"], type=["csv"], key=meta["key"],
            help=f"Required cols: {', '.join(sorted(meta['required_cols']))}",
        )

st.markdown("**Group B — Person & Organisation Files (7 files)**")
pcols1 = st.columns(4)
pcols2 = st.columns(3)
person_file_items = list(PERSON_FILES.items())
for idx, (name, meta) in enumerate(person_file_items[:4]):
    with pcols1[idx]:
        uploaded[name] = st.file_uploader(
            meta["label"], type=["csv"], key=meta["key"],
            help=f"Required cols: {', '.join(sorted(meta['required_cols']))}",
        )
for idx, (name, meta) in enumerate(person_file_items[4:]):
    with pcols2[idx]:
        uploaded[name] = st.file_uploader(
            meta["label"], type=["csv"], key=meta["key"],
            help=f"Required cols: {', '.join(sorted(meta['required_cols']))}",
        )

# ─────────────────────────────────────────────
#  RUN ANALYSIS
# ─────────────────────────────────────────────
st.markdown("---")

all_uploaded = all(v is not None for v in uploaded.values())
if not all_uploaded:
    missing = [ALL_FILES[k]["label"] for k, v in uploaded.items() if v is None]
    st.info(f"⏳ Waiting for: {', '.join(missing)}")
    st.stop()

run_btn = st.button("🚀 Run Analysis", type="primary", use_container_width=True)
if not run_btn:
    st.stop()

# ── Load all DataFrames ──────────────────────
with st.spinner("Loading files…"):
    dfs = {}
    valid = True
    for name, meta in ALL_FILES.items():
        df = load_csv(uploaded[name])
        if not validate_columns(df, meta["required_cols"], meta["label"]):
            valid = False
        dfs[name] = df

if not valid:
    st.stop()

# ── Unpack ───────────────────────────────────
dataset_le_raw   = dfs["dataset_legal_entity"].copy()
dataset_country  = dfs["dataset_country"].copy()
le_ref           = dfs["legal_entity_referential"].copy()
country_ref      = dfs["country_referential"].copy()
country_dkl      = dfs["country_dkl_api"].copy()
person_df        = dfs["person"].copy()
ep_owner         = dfs["entity_person_owner"].copy()
ep_owner_bk      = dfs["entity_person_owner_backup"].copy()
ep_manager       = dfs["entity_person_manager"].copy()
ep_manager_bk    = dfs["entity_person_manager_backup"].copy()

# ── Normalise core types ──────────────────────
dataset_le_raw["id_entity"]       = dataset_le_raw["id_entity"].astype(str).str.strip()
dataset_le_raw["id_legal_entity"] = pd.to_numeric(dataset_le_raw["id_legal_entity"], errors="coerce")
dataset_country["id_entity"]      = dataset_country["id_entity"].astype(str).str.strip()
dataset_country["code_iso_2a"]    = dataset_country["code_iso_2a"].astype(str).str.strip().str.upper()
le_ref["id_legal_entity"]         = pd.to_numeric(le_ref["id_legal_entity"], errors="coerce")
le_ref["id_country"]              = pd.to_numeric(le_ref["id_country"], errors="coerce")
country_ref["id_country"]         = pd.to_numeric(country_ref["id_country"], errors="coerce")
country_ref["code_iso_2a"]        = country_ref["code_iso_2a"].astype(str).str.strip().str.upper()

# ── Remove placeholder LE rows ────────────────
dataset_le = dataset_le_raw[
    dataset_le_raw["id_legal_entity"].notna() & (dataset_le_raw["id_legal_entity"] != 0)
].copy()

# ── Build LE enriched lookup ─────────────────
le_enriched = le_ref.merge(
    country_ref[["id_country", "code_iso_2a", "label_en"]].rename(
        columns={"code_iso_2a": "expected_iso", "label_en": "expected_country_label"}
    ),
    on="id_country", how="left",
)[["id_legal_entity", "elr_code", "full_name", "expected_iso", "expected_country_label"]]

# ── Country label map ─────────────────────────
country_label_map = (
    country_dkl[["code_iso_2a", "label_en"]]
    .drop_duplicates("code_iso_2a")
    .set_index("code_iso_2a")["label_en"]
    .to_dict()
)
for _, row in country_ref.iterrows():
    if row["code_iso_2a"] not in country_label_map:
        country_label_map[row["code_iso_2a"]] = row["label_en"]

# ── Person lookup ─────────────────────────────
person_lookup = build_person_lookup(person_df)

# ── Entity → person maps ──────────────────────
# Note: owner uses 'id_entity'; owner_backup uses 'entity_id'
owner_map        = build_entity_person_map(ep_owner,    "id_entity")
owner_backup_map = build_entity_person_map(ep_owner_bk, "entity_id")
manager_map      = build_entity_person_map(ep_manager,    "id_entity")
manager_backup_map = build_entity_person_map(ep_manager_bk, "id_entity")

# ── Entity sets ───────────────────────────────
entities_with_le      = set(dataset_le["id_entity"].unique())
entities_with_country = set(dataset_country["id_entity"].unique())
entities_both         = entities_with_le & entities_with_country
entities_le_only      = entities_with_le - entities_with_country
entities_country_only = entities_with_country - entities_with_le

# ─────────────────────────────────────────────
#  CLASSIFICATION — S1 / S2
# ─────────────────────────────────────────────
s1_rows, s2_rows = [], []

progress = st.progress(0, text="Classifying datasets…")
entities_both_list = sorted(entities_both)
total = len(entities_both_list)

for i, entity_id in enumerate(entities_both_list):
    if i % 200 == 0:
        progress.progress(int(i / total * 100), text=f"Classifying datasets… {i}/{total}")

    le_ids      = dataset_le[dataset_le["id_entity"] == entity_id]["id_legal_entity"].unique()
    actual_isos = set(dataset_country[dataset_country["id_entity"] == entity_id]["code_iso_2a"].unique())
    le_detail   = le_enriched[le_enriched["id_legal_entity"].isin(le_ids)]

    expected_isos       = set(le_detail["expected_iso"].dropna().unique())
    le_has_null_country = le_detail["expected_iso"].isna().any()
    expected_str        = format_iso_set(expected_isos, country_label_map) if expected_isos else "No country defined in referential"

    pcols = get_person_cols(entity_id, owner_map, owner_backup_map,
                            manager_map, manager_backup_map, person_lookup)

    for iso in actual_isos:
        label      = country_label_map.get(iso, "")
        is_valid   = iso in expected_isos
        actual_str = f"{iso} ({label})" if label else iso

        le_rows_iter = le_detail.to_dict("records") if not le_detail.empty else [
            {"id_legal_entity": None, "full_name": "NOT FOUND IN REFERENTIAL", "elr_code": None, "expected_iso": None}
        ]

        for le_row in le_rows_iter:
            le_id   = le_row["id_legal_entity"]
            le_name = le_row["full_name"]

            base = {
                "id_entity":         entity_id,
                **pcols,
                "id_legal_entity":   int(le_id) if le_id is not None and pd.notna(le_id) else None,
                "legal_entity_name": le_name,
                "elr_code":          le_row["elr_code"],
                "country_iso":       iso,
                "country_label_en":  label,
            }

            if is_valid:
                base["scenario_note"] = (
                    f"Valid — '{le_name}' is correctly mapped to {actual_str}, "
                    f"which matches the referential"
                )
                s1_rows.append(base)
            else:
                if le_has_null_country and not expected_isos:
                    note = (
                        f"Mismatch — Dataset is mapped to {actual_str}, but legal entity "
                        f"'{le_name}' has NO country defined in the referential "
                        f"(expected: nothing on record)"
                    )
                elif le_has_null_country and expected_isos:
                    note = (
                        f"Mismatch — Dataset is mapped to {actual_str}, but legal entity "
                        f"'{le_name}' should map to {expected_str} per referential "
                        f"(note: some linked legal entities also have no country in the referential)"
                    )
                else:
                    note = (
                        f"Mismatch — Dataset is mapped to {actual_str}, but legal entity "
                        f"'{le_name}' should map to {expected_str} per referential"
                    )
                base["scenario_note"] = note
                s2_rows.append(base)

progress.progress(100, text="Classification complete.")

# ─────────────────────────────────────────────
#  SCENARIO 3 — LE but NO country
# ─────────────────────────────────────────────
s3_rows = []
for entity_id in entities_le_only:
    le_ids    = dataset_le[dataset_le["id_entity"] == entity_id]["id_legal_entity"].unique()
    le_detail = le_enriched[le_enriched["id_legal_entity"].isin(le_ids)]
    pcols     = get_person_cols(entity_id, owner_map, owner_backup_map,
                                manager_map, manager_backup_map, person_lookup)

    if le_detail.empty:
        s3_rows.append({
            "id_entity": entity_id, **pcols,
            "id_legal_entity": None,
            "legal_entity_name": "NOT FOUND IN REFERENTIAL",
            "elr_code": None, "country_iso": None, "country_label_en": None,
            "scenario_note": "No country assigned — legal entity exists in dataset but no country is linked",
        })
    else:
        for _, le_row in le_detail.iterrows():
            exp_iso   = le_row.get("expected_iso")
            exp_label = le_row.get("expected_country_label")
            if pd.notna(exp_iso):
                note = (
                    f"No country assigned — legal entity '{le_row['full_name']}' should map to "
                    f"{exp_iso} ({exp_label}) per referential, but no country is linked to this dataset"
                )
            else:
                note = (
                    f"No country assigned — legal entity '{le_row['full_name']}' also has no country "
                    f"defined in the referential"
                )
            s3_rows.append({
                "id_entity": entity_id, **pcols,
                "id_legal_entity":   int(le_row["id_legal_entity"]) if pd.notna(le_row["id_legal_entity"]) else None,
                "legal_entity_name": le_row["full_name"],
                "elr_code":          le_row["elr_code"],
                "country_iso":       None,
                "country_label_en":  None,
                "scenario_note":     note,
            })

# ─────────────────────────────────────────────
#  SCENARIO 4 — Country but NO LE
# ─────────────────────────────────────────────
s4_rows = []
for entity_id in entities_country_only:
    actual_isos = dataset_country[dataset_country["id_entity"] == entity_id]["code_iso_2a"].unique()
    pcols       = get_person_cols(entity_id, owner_map, owner_backup_map,
                                  manager_map, manager_backup_map, person_lookup)
    for iso in actual_isos:
        label = country_label_map.get(iso, "")
        s4_rows.append({
            "id_entity": entity_id, **pcols,
            "id_legal_entity": None,
            "legal_entity_name": None,
            "elr_code": None,
            "country_iso": iso,
            "country_label_en": label,
            "scenario_note": (
                f"No legal entity assigned — dataset has country {iso} ({label}) "
                f"but no legal entity is linked to this dataset"
            ),
        })

# ─────────────────────────────────────────────
#  BUILD DataFrames
# ─────────────────────────────────────────────
def make_df(rows):
    if not rows:
        return pd.DataFrame(columns=OUTPUT_COLS)
    return pd.DataFrame(rows)[OUTPUT_COLS].drop_duplicates().reset_index(drop=True)


s2_entity_ids = set(r["id_entity"] for r in s2_rows)
s1_rows_clean = [r for r in s1_rows if r["id_entity"] not in s2_entity_ids]

df_s1 = make_df(s1_rows_clean)
df_s2 = make_df(s2_rows)
df_s3 = make_df(s3_rows)
df_s4 = make_df(s4_rows)

scenario_dfs = {
    "scenario1_valid":            df_s1,
    "scenario2_country_mismatch": df_s2,
    "scenario3_le_no_country":    df_s3,
    "scenario4_country_no_le":    df_s4,
}

# ─────────────────────────────────────────────
#  SUMMARY METRICS
# ─────────────────────────────────────────────
st.markdown("---")
st.subheader("📊 Step 2 — Summary")

m1, m2, m3, m4 = st.columns(4)
m1.metric("✅ Scenario 1 — Valid",             f"{df_s1['id_entity'].nunique():,} datasets  ({len(df_s1):,} rows)")
m2.metric("⚠️  Scenario 2 — Country Mismatch", f"{df_s2['id_entity'].nunique():,} datasets  ({len(df_s2):,} rows)")
m3.metric("🔴 Scenario 3 — LE, No Country",    f"{df_s3['id_entity'].nunique():,} datasets  ({len(df_s3):,} rows)")
m4.metric("🔵 Scenario 4 — Country, No LE",    f"{df_s4['id_entity'].nunique():,} datasets  ({len(df_s4):,} rows)")

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
        "**Scenario 1 — All Valid:** Every country on the dataset matches the referential mapping "
        "for its legal entity. No migration action needed."
    ),
    "tab2": (
        "**Scenario 2 — Country Mismatch:** At least one country does NOT match the referential. "
        "`scenario_note` reads: *'Dataset is mapped to X, but legal entity Y should map to Z per referential'*."
    ),
    "tab3": (
        "**Scenario 3 — Legal Entity, No Country:** Legal entity linked but zero countries assigned. "
        "The note shows what country should be added."
    ),
    "tab4": (
        "**Scenario 4 — Country, No Legal Entity:** Country assigned but no legal entity linked."
    ),
}


def show_tab(tab, df, desc):
    with tab:
        st.markdown(desc)
        st.dataframe(df, use_container_width=True, height=420)
        st.caption(f"{len(df):,} rows  ·  {df['id_entity'].nunique():,} unique datasets")


show_tab(tab1, df_s1, SCENARIO_DESCRIPTIONS["tab1"])
show_tab(tab2, df_s2, SCENARIO_DESCRIPTIONS["tab2"])
show_tab(tab3, df_s3, SCENARIO_DESCRIPTIONS["tab3"])
show_tab(tab4, df_s4, SCENARIO_DESCRIPTIONS["tab4"])

# ─────────────────────────────────────────────
#  DOWNLOADS
# ─────────────────────────────────────────────
st.markdown("---")
st.subheader("⬇️ Step 4 — Download Reports")
st.caption(
    "All CSVs use UTF-8 BOM — accented characters (SOCIÉTÉ GÉNÉRALE, é, è …) "
    "display correctly when opened in Excel. "
    "Multiple owners/managers are separated by semicolons in the same cell."
)

col1, col2, col3, col4, col_all = st.columns(5)

with col1:
    st.download_button(
        label="✅ Scenario 1\n(Valid)",
        data=df_to_csv_bytes(df_s1),
        file_name="scenario1_valid.csv",
        mime="text/csv",
        use_container_width=True,
    )
with col2:
    st.download_button(
        label="⚠️ Scenario 2\n(Mismatch)",
        data=df_to_csv_bytes(df_s2),
        file_name="scenario2_country_mismatch.csv",
        mime="text/csv",
        use_container_width=True,
    )
with col3:
    st.download_button(
        label="🔴 Scenario 3\n(LE, No Country)",
        data=df_to_csv_bytes(df_s3),
        file_name="scenario3_le_no_country.csv",
        mime="text/csv",
        use_container_width=True,
    )
with col4:
    st.download_button(
        label="🔵 Scenario 4\n(Country, No LE)",
        data=df_to_csv_bytes(df_s4),
        file_name="scenario4_country_no_le.csv",
        mime="text/csv",
        use_container_width=True,
    )
with col_all:
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
