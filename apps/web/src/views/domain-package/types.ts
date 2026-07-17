export type PackageLayer =
  | 'PLATFORM_STANDARD'
  | 'INDUSTRY'
  | 'WORKSPACE_CUSTOMER'

export type PackageStatus = 'DRAFT' | 'ACTIVE' | 'DEPRECATED' | 'RETIRED'
export type PackageVersionStatus =
  | 'DRAFT'
  | 'VALIDATED'
  | 'REVIEW_PENDING'
  | 'APPROVED'
  | 'PUBLISHED'
  | 'RETIRED'
  | 'ROLLED_BACK'

export interface DomainPackage {
  id: string
  workspaceId?: string
  stableKey: string
  name: string
  layer: PackageLayer
  parentPackageId?: string | null
  status: PackageStatus
  revision: number
}

export interface DomainPackagePage {
  items: DomainPackage[]
  nextCursor: string | null
}

export interface FieldDefinition {
  stableKey: string
  label: Record<string, string>
  dataType: string
  required?: boolean
  sensitive?: boolean
}

export interface StateDefinition {
  stableKey: string
  label: Record<string, string>
  topLifecycleState: string
  initial?: boolean
  terminal?: boolean
}

export interface ObjectDefinition {
  stableKey: string
  kind: 'CORE_EXTENSION' | 'NEW_OBJECT'
  label: Record<string, string>
  fields: FieldDefinition[]
  relations: unknown[]
  states: StateDefinition[]
  transitions: unknown[]
}

export interface DomainPackageManifest {
  schemaVersion: string
  stableKey: string
  name: string
  description?: string
  layer: PackageLayer
  version: string
  extends?: { packageKey: string; versionRange: string }
  objects: ObjectDefinition[]
  pages: unknown[]
  views: unknown[]
  rules: unknown[]
  workflowBindings: unknown[]
  permissions: unknown[]
  extensions: unknown[]
  overrides: unknown[]
  migrations: unknown[]
}

export interface DomainPackageVersion {
  id: string
  packageId: string
  semanticVersion: string
  contentHash: string
  status: PackageVersionStatus
  frozen: boolean
  revision: number
}

export interface ValidationReport {
  valid: boolean
  errors: string[]
  warnings: string[]
  compatibility: 'COMPATIBLE' | 'MIGRATION_REQUIRED' | 'BREAKING'
}

export interface MigrationPreview {
  previewId: string
  affectedInstances: number
  conflicts: string[]
  batches: number
  rollbackAvailable: boolean
  confirmationToken: string
}

export interface MigrationJob {
  id: string
  sourceVersionId: string
  targetVersionId: string
  status: string
  migrated: number
  failed: number
  revision: number
}

export interface DomainPackageDesignerApi {
  list(workspaceId: string, layer?: PackageLayer): Promise<DomainPackagePage>
  createPackage(
    workspaceId: string,
    command: Record<string, unknown>,
  ): Promise<DomainPackage>
  createVersion(
    workspaceId: string,
    packageId: string,
    command: Record<string, unknown>,
  ): Promise<DomainPackageVersion>
  validate(
    workspaceId: string,
    packageId: string,
    versionId: string,
    revision: number,
  ): Promise<ValidationReport>
  submitReview(
    workspaceId: string,
    packageId: string,
    versionId: string,
    revision: number,
  ): Promise<DomainPackageVersion>
  review(
    workspaceId: string,
    packageId: string,
    versionId: string,
    revision: number,
    comment: string,
  ): Promise<DomainPackageVersion>
  publish(
    workspaceId: string,
    packageId: string,
    versionId: string,
    revision: number,
    reviewComment: string,
  ): Promise<DomainPackageVersion>
  previewMigration(
    workspaceId: string,
    packageId: string,
    targetVersionId: string,
    command: Record<string, unknown>,
  ): Promise<MigrationPreview>
  startMigration(
    workspaceId: string,
    packageId: string,
    targetVersionId: string,
    command: Record<string, unknown>,
  ): Promise<MigrationJob>
}
