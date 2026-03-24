import React from 'react';

import { ControlProps } from '@jsonforms/core';
import { withJsonFormsControlProps } from '@jsonforms/react';

const SgInputControl = ({
  data,
  label,
  visible,
  enabled,
  path,
  config,
}: ControlProps) => {
  if (!visible) {
    return null;
  }

  const isReadOnly = !enabled;

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!isReadOnly && config?.onLocalChange) {
      config.onLocalChange(path, e.target.value);
    }
  };

  if (isReadOnly) {
    return (
      <div className="d-flex flex-column mb-3">
        <label className="text-secondary font-weight-medium mb-1">{label}</label>
        <input
          type="text"
          className="form-control"
          value={data !== undefined && data !== null ? String(data) : ''}
          disabled
        />
      </div>
    );
  }

  return (
    <div className="d-flex flex-column mb-3">
      <label className="text-secondary font-weight-medium mb-1">{label}</label>
      <input
        type="text"
        className="form-control"
        style={{ backgroundColor: '#e9ecef', opacity: 1, color: '#212529' }}
        value={data !== undefined && data !== null ? String(data) : ''}
        onChange={handleChange}
      />
    </div>
  );
};

export default withJsonFormsControlProps(SgInputControl);
