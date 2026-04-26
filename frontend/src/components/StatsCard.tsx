interface Props {
  title: string
  value: string | number
  subtitle?: string
  color?: 'blue' | 'green' | 'yellow' | 'red' | 'gray'
}

const colorMap = {
  blue:   'border-blue-500 text-blue-400',
  green:  'border-green-500 text-green-400',
  yellow: 'border-yellow-500 text-yellow-400',
  red:    'border-red-500 text-red-400',
  gray:   'border-gray-500 text-gray-400',
}

export default function StatsCard({ title, value, subtitle, color = 'blue' }: Props) {
  const colors = colorMap[color]
  return (
    <div className={`bg-gray-900 rounded-xl p-5 border-l-4 ${colors.split(' ')[0]}`}>
      <p className="text-sm text-gray-400 font-medium">{title}</p>
      <p className={`text-3xl font-bold mt-1 ${colors.split(' ')[1]}`}>{value}</p>
      {subtitle && <p className="text-xs text-gray-500 mt-1">{subtitle}</p>}
    </div>
  )
}
