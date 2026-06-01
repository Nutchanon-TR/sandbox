export interface PageResponse<T> {
    items: T[];
    hasMore: boolean;
    nextCursor: number | null;
}

export interface JobjabProfile {
    id: number;
    userId: number;
    headline?: string;
    desiredTitles: string[];
    skills: string[];
    experiences: string[];
    preferredLocations: string[];
    employmentTypes: string[];
    salaryMin?: number;
    salaryMax?: number;
    homeLocationLabel?: string;
    homeLatitude?: number;
    homeLongitude?: number;
    maxCommuteMinutes?: number;
    travelMode: string;
    weeklyDigestEnabled: boolean;
    createdAt?: string;
    updatedAt?: string;
}

export interface JobjabProfilePayload {
    headline?: string;
    desiredTitles: string[];
    skills: string[];
    experiences: string[];
    preferredLocations: string[];
    employmentTypes: string[];
    salaryMin?: number;
    salaryMax?: number;
    homeLocationLabel?: string;
    homeLatitude?: number;
    homeLongitude?: number;
    maxCommuteMinutes?: number;
    travelMode?: string;
    weeklyDigestEnabled?: boolean;
}

export interface JobSource {
    id: number;
    sourceKey: string;
    displayName: string;
    fetchMode: string;
    enabled: boolean;
    registered: boolean;
    configured: boolean;
    searchable: boolean;
    acceptsPerSearchTargets: boolean;
    requiresJobUrl: boolean;
    requiresPartnerApproval: boolean;
    robotsCheckRequired: boolean;
    rateLimitPerMinute?: number;
    targetHint?: string;
    targetExample?: string;
    guidance?: string;
}

export interface ManualJobInput {
    title?: string;
    company?: string;
    locationText?: string;
    locationLatitude?: number;
    locationLongitude?: number;
    salaryMin?: number;
    salaryMax?: number;
    currency?: string;
    employmentType?: string;
    workplaceType?: string;
    skills?: string[];
    description?: string;
    applyUrl?: string;
}

export interface SearchRunRequest {
    query?: string;
    locationText?: string;
    limit?: number;
    jobUrl?: string;
    jobUrls?: string[];
    manualJobs?: ManualJobInput[];
    sourceTargets?: Record<string, string[]>;
    sourceKeys?: string[];
}

export interface SearchRun {
    id: number;
    userId: number;
    profileId?: number;
    runType: string;
    status: string;
    query?: string;
    locationText?: string;
    requestedLimit: number;
    sourceKeys: string[];
    startedAt?: string;
    completedAt?: string;
    createdAt: string;
}

export interface SearchRunEvent {
    id: number;
    searchRunId: number;
    level: string;
    sourceKey?: string;
    message: string;
    payload: Record<string, unknown>;
    createdAt: string;
}

export interface JobMatch {
    id: number;
    userId: number;
    jobId: number;
    profileId?: number;
    matchScore: number;
    matchedSkills: string[];
    missingSkills: string[];
    redFlags: string[];
    aiSummary: string;
    analyzedAt: string;
}

export interface JobTracking {
    id: number;
    userId: number;
    jobId: number;
    status: TrackingStatus;
    notes?: string;
    appliedAt?: string;
    interviewAt?: string;
    createdAt: string;
    updatedAt: string;
}

export type TrackingStatus = 'INTERESTED' | 'APPLIED' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'ARCHIVED';

export interface Job {
    id: number;
    sourceId: number;
    sourceJobKey: string;
    canonicalUrl?: string;
    title: string;
    company: string;
    locationText?: string;
    locationLatitude?: number;
    locationLongitude?: number;
    salaryMin?: number;
    salaryMax?: number;
    currency: string;
    employmentType?: string;
    workplaceType?: string;
    skills: string[];
    description: string;
    applyUrl?: string;
    postedAt?: string;
    firstSeenAt: string;
    lastSeenAt: string;
    match?: JobMatch | null;
    tracking?: JobTracking | null;
    route?: RouteResult | null;
}

export interface RouteResult {
    id: number;
    userId: number;
    jobId: number;
    travelMode: string;
    originLabel?: string;
    destinationLabel?: string;
    distanceMeters?: number;
    durationSeconds?: number;
    provider: string;
    createdAt: string;
    expiresAt?: string;
}

export interface RouteRequest {
    travelMode?: string;
}

export interface WeeklyDigestItem {
    id: number;
    weeklyDigestId: number;
    jobId: number;
    matchId?: number;
    rank: number;
    reason?: string;
    job: Job;
}

export interface WeeklyDigest {
    id: number;
    userId: number;
    profileId?: number;
    status: string;
    weekStart: string;
    weekEnd: string;
    summary?: string;
    createdAt: string;
    sentAt?: string;
    items: WeeklyDigestItem[];
}
