interface Props {
  decision: string
  score?: number
}

const config = {
  APPROVED:       { bg: 'bg-green-900', text: 'text-green-300',  label: 'Approved'       },
  REVIEW_REQUIRED:{ bg: 'bg-yellow-900',text: 'text-yellow-300', label: 'Review Required' },
  BLOCKED:        { bg: 'bg-red-900',   text: 'text-red-300',    label: 'Blocked'         },
  PENDING:        { bg: 'bg-gray-700',  text: 'text-gray-300',   label: 'Pending'         },
}

export default function RiskBadge({ decision, score }: Props) {
  const c = config[decision as keyof typeof config] ?? config.PENDING
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium ${c.bg} ${c.text}`}>
      {c.label}
      {score !== undefined && <span className="opacity-75">({score})</span>}
    </span>
  )
}
