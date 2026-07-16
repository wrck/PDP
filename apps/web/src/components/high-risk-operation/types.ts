export interface HighRiskOperationPreview {
  previewId: string
  operationType: string
  targetSummary: string
  affectedCounts: Record<string, number>
  warnings: string[]
  irreversibleAt: string
  compensation?: string
  confirmationPhrase: string
  confirmationToken: string
  expiresAt: string
  availability: {
    enabled: boolean
    reasonCode: string
    reason: string
  }
}
