export interface TransactionSaveApi<Payload, Record extends { id: number }, Image> {
  create: (payload: Payload) => Promise<Record>
  appendImages: (id: number, images: Image[]) => Promise<unknown>
}

export interface TransactionSaveResult<Record> {
  record: Record
  imageUploadError?: unknown
}

export async function saveTransactionWithOptionalImages<Payload, Record extends { id: number }, Image>(
  api: TransactionSaveApi<Payload, Record, Image>,
  payload: Payload,
  images: Image[]
): Promise<TransactionSaveResult<Record>> {
  const record = await api.create(payload)
  if (images.length === 0) {
    return { record }
  }
  try {
    await api.appendImages(record.id, images)
    return { record }
  } catch (imageUploadError) {
    return { record, imageUploadError }
  }
}
