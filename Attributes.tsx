import React, { MutableRefObject, useCallback, useEffect, useRef, useState } from 'react';

import { JsonForms } from '@jsonforms/react';
import { vanillaRenderers, vanillaCells } from '@jsonforms/vanilla-renderers';
import { useMyWidgetConfiguration } from '@sg-widgets/react-core';
import { FormattedMessage } from 'react-intl';

import { Loader } from '../../../../../../../../common/components/shared/Loader';
import { MyConfiguration } from '../../../../../../../../common/my-configuration';
import { AttributeService } from '../../../../../../../../services/attribute.service';
import {
  AttributeCategory,
  AttributeEntry,
  AttributeViewResponse,
} from '../../../../../../../../services/attribute.service.types';
import { ServiceError } from '../../../../../../shared/ServiceError';
import SgInputControl from './SgInputControl';
import SgInputControlTester from './SgInputControlTester';

interface AttributesProps {
  tableId: number;
}

const customRenderers = [
  ...vanillaRenderers,
  { tester: SgInputControlTester, renderer: SgInputControl },
];

const chunkAttributes = (attributes: AttributeEntry[], size: number): AttributeEntry[][] => {
  const chunks: AttributeEntry[][] = [];
  for (let i = 0; i < attributes.length; i += size) {
    chunks.push(attributes.slice(i, i + size));
  }
  return chunks;
};

const buildSingleAttrProps = (attr: AttributeEntry, localValues: Record<string, string>) => {
  const currentValue = localValues[attr.technicalName] !== undefined
    ? localValues[attr.technicalName]
    : (attr.value !== undefined && attr.value !== null ? String(attr.value) : '');

  const schema = {
    type: 'object',
    properties: {
      [attr.technicalName]: {
        type: attr.dataType,
        title: attr.name,
      },
    },
  };
  const uischema = {
    type: 'Control',
    scope: `#/properties/${attr.technicalName}`,
  };
  const data = { [attr.technicalName]: currentValue };
  return { schema, uischema, data };
};

const Attributes: React.FC<AttributesProps> = ({ tableId }: AttributesProps) => {
  const configuration: MyConfiguration | undefined = useMyWidgetConfiguration<MyConfiguration>();
  const abortControllerRef: MutableRefObject<AbortController | undefined> = useRef<AbortController>();

  const [attributeData, setAttributeData] = useState<AttributeViewResponse | undefined>();
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isError, setIsError] = useState<boolean>(false);
  const [saveError, setSaveError] = useState<string | undefined>();
  const [isSaving, setIsSaving] = useState<boolean>(false);

  // Local state per category — tracks edited values before save
  // Key: category name, Value: map of technicalName -> edited value
  const [localValues, setLocalValues] = useState<Record<string, Record<string, string>>>({});

  const fetchAttributeValues = useCallback(() => {
    if (!configuration || !tableId) return;
    abortControllerRef.current = new AbortController();
    setIsLoading(true);
    setIsError(false);
    setSaveError(undefined);

    AttributeService.getAttributeValues(
      configuration.dataCatalogApiBaseUrl,
      tableId,
      abortControllerRef.current.signal
    )
      .then((response: AttributeViewResponse | void) => {
        setAttributeData(response || undefined);
        setLocalValues({});
        setIsLoading(false);
      })
      .catch(() => {
        setIsError(true);
        setIsLoading(false);
      });
  }, [configuration, tableId]);

  useEffect(() => {
    fetchAttributeValues();
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [fetchAttributeValues]);

  const handleLocalChange = (category: string, technicalName: string, value: string) => {
    setLocalValues((prev) => ({
      ...prev,
      [category]: {
        ...(prev[category] || {}),
        [technicalName]: value,
      },
    }));
  };

  const handleSave = (categoryData: AttributeCategory) => {
    if (!configuration) return;
    setIsSaving(true);
    setSaveError(undefined);

    const categoryLocalValues = localValues[categoryData.category] || {};
    const editableAttrs = categoryData.attributes.filter((a) => !a.readOnly);

    const valuesToSave: Record<string, string> = {};
    editableAttrs.forEach((attr) => {
      const edited = categoryLocalValues[attr.technicalName];
      if (edited !== undefined) {
        valuesToSave[attr.technicalName] = edited;
      } else {
        valuesToSave[attr.technicalName] = attr.value !== null && attr.value !== undefined
          ? String(attr.value)
          : '';
      }
    });

    AttributeService.upsertAttributeValues(
      configuration.dataCatalogApiBaseUrl,
      tableId,
      { category: categoryData.category, values: valuesToSave }
    )
      .then(() => {
        setIsSaving(false);
        fetchAttributeValues();
      })
      .catch(() => {
        setIsSaving(false);
        setSaveError('Failed to save. Please try again.');
      });
  };

  const categoryHasEditableField = (categoryData: AttributeCategory): boolean =>
    categoryData.attributes.some((attr) => !attr.readOnly);

  if (isLoading) {
    return <Loader divContainerClass="d-flex justify-content-center w-100 mt-4" loadingSpanClass="display-5" />;
  }

  if (isError) {
    return <ServiceError section="attribute values" />;
  }

  if (!attributeData || attributeData.categories.length === 0) {
    return (
      <div className="d-flex flex-column align-items-center justify-content-center p-5 text-secondary">
        <i className="icon icon-md mb-2">info</i>
        <span>
          <FormattedMessage id="dataset.dataSource.details.tabs.tables.details.tabs.attributes.empty" />
        </span>
      </div>
    );
  }

  return (
    <div style={{ overflowY: 'auto', maxHeight: 'calc(100vh - 320px)', padding: '1.5rem' }}>
      {saveError && (
        <div className="alert alert-danger mb-3" role="alert">
          {saveError}
        </div>
      )}
      {attributeData.categories.map((categoryData: AttributeCategory) => {
        const rows = chunkAttributes(categoryData.attributes, 3);
        const hasEditable = categoryHasEditableField(categoryData);
        const catLocalValues = localValues[categoryData.category] || {};

        return (
          <div key={categoryData.category} style={{ marginBottom: '2rem', marginTop: '1rem' }}>
            <div className="d-flex align-items-center justify-content-between mb-3">
              <h5 className="mb-0" style={{ fontWeight: 600 }}>{categoryData.category}</h5>
              {hasEditable && (
                <button
                  type="button"
                  className="btn btn-md btn-primary"
                  disabled={isSaving}
                  onClick={() => handleSave(categoryData)}
                >
                  {isSaving ? 'Saving...' : 'Save'}
                </button>
              )}
            </div>
            <div className="card card-bordered p-4">
              {rows.map((rowAttrs: AttributeEntry[], rowIndex: number) => (
                <div key={rowIndex} className="row mb-2">
                  {rowAttrs.map((attr: AttributeEntry) => {
                    const { schema, uischema, data: formData } = buildSingleAttrProps(attr, catLocalValues);
                    return (
                      <div key={attr.attributeId} className="col-4">
                        <JsonForms
                          schema={schema}
                          uischema={uischema}
                          data={formData}
                          renderers={customRenderers}
                          cells={vanillaCells}
                          readonly={attr.readOnly}
                          onChange={() => {}}
                          config={{
                            onLocalChange: (path: string, value: string) =>
                              handleLocalChange(categoryData.category, attr.technicalName, value),
                          }}
                        />
                      </div>
                    );
                  })}
                </div>
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default Attributes;
