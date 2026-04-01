import { CanceledError } from 'axios';

import { DXError } from '../common/errors';
import { getAxios } from '../common/utils';
import { AttributeViewResponse } from './attribute.service.types';

export class AttributeService {
  static async getAttributeValues(
    dataCatalogApiBaseUrl: string,
    tableId: number,
    abortSignal?: AbortSignal
  ): Promise<AttributeViewResponse | void> {
    try {
      const url = `${dataCatalogApiBaseUrl}/data-source-tables/${tableId}/attributes`;
      const response = await getAxios('GET', url, { signal: abortSignal });
      return response.data as AttributeViewResponse;
    } catch (err) {
      if (!(err instanceof CanceledError)) {
        throw DXError.fromAxios('Failed to get attribute values', err);
      }
    }
  }

  static async upsertAttributeValues(
    dataCatalogApiBaseUrl: string,
    tableId: number,
    payload: { category: string; values: Record<string, unknown> }
  ): Promise<void> {
    try {
      const url = `${dataCatalogApiBaseUrl}/data-source-tables/${tableId}/attributes`;
      await getAxios('POST', url, { data: payload });
    } catch (err) {
      throw DXError.fromAxios('Failed to save attribute value', err);
    }
  }
}
