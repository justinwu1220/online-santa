/** 圖片檔（可縮圖、可放大）；PDF 等其他檔案維持連結。 */
export const isImage = (contentType?: string) => Boolean(contentType?.startsWith('image/'))
