/** 格式化高精度小数，去掉无意义尾零 */
export const formatDecimal = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  const text = String(value)
  if (!text.includes('.')) return text
  return text
    .replace(/(\.\d*?[1-9])0+$/, '$1')
    .replace(/\.0+$/, '')
}

/** 格式化积分显示 */
export const formatPoints = (value) => formatDecimal(value)

/** 格式化兑换比例显示 */
export const formatRatio = (value) => formatDecimal(value)

/** 判断积分变动是否为正数 */
export const isPositiveDecimal = (value) => Number(value) > 0