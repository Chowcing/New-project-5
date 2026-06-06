export const MAX_TRANSACTION_IMAGES = 3
export const MAX_TRANSACTION_IMAGE_SIZE = 5 * 1024 * 1024
export const ALLOWED_TRANSACTION_IMAGE_TYPES = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/heic',
  'image/heif',
  'image/heic-sequence',
  'image/heif-sequence'
]
export const ALLOWED_TRANSACTION_IMAGE_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.webp', '.heic', '.heif']
export const TRANSACTION_IMAGE_ACCEPT = 'image/jpeg,image/png,image/webp,image/heic,image/heif,image/heic-sequence,image/heif-sequence,.heic,.heif'

export function isAllowedTransactionImageFile(file: File) {
  const filename = file.name.toLowerCase()
  const contentType = file.type.toLowerCase()
  return ALLOWED_TRANSACTION_IMAGE_TYPES.includes(contentType)
    || ALLOWED_TRANSACTION_IMAGE_EXTENSIONS.some((extension) => filename.endsWith(extension))
}
