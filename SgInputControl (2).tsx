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

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (enabled && config?.onLocalChange) {
      config.onLocalChange(path, e.target.value);
    }
  };

  return (
    <div className="d-flex flex-column mb-3">
      <label className="text-secondary font-weight-medium mb-1">{label}</label>
      <input
        type="text"
        className="form-control bg-white"
        value={data !== undefined && data !== null ? String(data) : ''}
        readOnly={!enabled}
        onChange={handleChange}
      />
    </div>
  );
};

export default withJsonFormsControlProps(SgInputControl);
