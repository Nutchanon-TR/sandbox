import api from '@/config/axiosConfig';
import { ApiDetail } from '@/interface/common/ApiDetail';

export async function apiCall<T>(
  apiDetail: ApiDetail, 
  request: Record<any, any> = {},
  pathParams: Record<string, string | number> = {}
): Promise<T> {
  let url = apiDetail.path;
  const method = (apiDetail.method || 'POST').toUpperCase();

  // Replace {paramName} in the URL
  Object.keys(pathParams).forEach(key => {
    url = url.replace(`{${key}}`, String(pathParams[key]));
  });

  const response = await api({
    method,
    url: url,
    ...(method === 'GET' ? { params: request } : { data: request }),
  });
  return response.data;
}

export async function fetchApi<T>(apiDetail: ApiDetail, request: Record<any, any> = {}, pathParams: Record<string, string | number> = {}): Promise<T> {
  return apiCall<T>(apiDetail, request, pathParams);
}

export async function fetchData<T>(apiDetail: ApiDetail, request: Record<any, any> = {}, pathParams: Record<string, string | number> = {}): Promise<T> {
  return apiCall<T>(apiDetail, request, pathParams);
}

export async function submitData<T>(apiDetail: ApiDetail, request: Record<any, any> = {}, pathParams: Record<string, string | number> = {}): Promise<T> {
  return apiCall<T>(apiDetail, request, pathParams);
}

export async function downloadFile(apiDetail: ApiDetail, request: Record<any, any> = {}, pathParams: Record<string, string | number> = {}): Promise<Blob> {
  let url = apiDetail.path;

  Object.keys(pathParams).forEach(key => {
    url = url.replace(`{${key}}`, String(pathParams[key]));
  });

  const response = await api({
    method: 'POST',
    url: url,
    data: request,
    responseType: 'blob',
  });
  return response.data; // This will be a Blob
}
