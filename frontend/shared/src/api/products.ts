import type { AxiosInstance } from 'axios';
import type { Page, Product } from '../types';

export async function listProducts(
  client: AxiosInstance,
  page = 0,
  size = 20
): Promise<Page<Product>> {
  const { data } = await client.get<Page<Product>>('/api/products', {
    params: { page, size },
  });
  return data;
}

export async function getProduct(client: AxiosInstance, id: number): Promise<Product> {
  const { data } = await client.get<Product>(`/api/products/${id}`);
  return data;
}
