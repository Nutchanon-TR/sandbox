'use client';

import { AimOutlined, ArrowRightOutlined, CheckCircleOutlined, ClockCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { Button, Progress, Select, Tag } from 'antd';
import Link from 'next/link';
import type { Job, JobMatch, TrackingStatus } from '@/interface/Jobjab';

export const TRACKING_COLUMNS: { key: TrackingStatus; label: string }[] = [
    { key: 'INTERESTED', label: 'Interested' },
    { key: 'APPLIED', label: 'Applied' },
    { key: 'INTERVIEW', label: 'Interview' },
    { key: 'OFFER', label: 'Offer' },
    { key: 'REJECTED', label: 'Rejected' },
    { key: 'ARCHIVED', label: 'Archived' },
];

export function formatSalary(job: Job) {
    if (job.salaryMin == null && job.salaryMax == null) return 'Salary not listed';
    if (job.salaryMin != null && job.salaryMax != null) return `${job.salaryMin.toLocaleString()}-${job.salaryMax.toLocaleString()} ${job.currency}`;
    if (job.salaryMin != null) return `From ${job.salaryMin.toLocaleString()} ${job.currency}`;
    return `Up to ${job.salaryMax?.toLocaleString()} ${job.currency}`;
}

export function formatDuration(seconds?: number) {
    if (!seconds) return 'N/A';
    const minutes = Math.round(seconds / 60);
    if (minutes < 60) return `${minutes} min`;
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return rest === 0 ? `${hours} hr` : `${hours} hr ${rest} min`;
}

export function formatDistance(meters?: number) {
    if (!meters) return 'N/A';
    if (meters < 1000) return `${meters} m`;
    return `${(meters / 1000).toFixed(1)} km`;
}

export function JobSummaryCard({
    job,
    onAnalyze,
    onTrack,
    loading,
}: {
    job: Job;
    onAnalyze?: (job: Job) => void;
    onTrack?: (job: Job) => void;
    loading?: boolean;
}) {
    return (
        <div className="rounded-md border border-slate-300 bg-surface p-4 shadow-sm dark:border-border-main">
            <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                        {job.match && (
                            <Tag color={job.match.matchScore >= 75 ? 'green' : job.match.matchScore >= 50 ? 'gold' : 'red'}>
                                {job.match.matchScore}% match
                            </Tag>
                        )}
                        {job.route?.durationSeconds != null && (
                            <Tag color="cyan" icon={<ClockCircleOutlined />}>
                                {formatDuration(job.route.durationSeconds)}
                            </Tag>
                        )}
                        {job.tracking && <Tag color="blue">{job.tracking.status}</Tag>}
                    </div>
                    <Link href={`/jobjab/jobs/${job.id}`} className="mt-2 block text-lg font-semibold text-foreground hover:text-blue-600">
                        {job.title}
                    </Link>
                    <div className="mt-1 text-sm text-text-secondary">
                        {job.company} - {job.locationText || 'Location not listed'} - {formatSalary(job)}
                    </div>
                    {job.match?.missingSkills.length ? (
                        <p className="mt-2 text-sm text-orange-600">
                            Needs: {job.match.missingSkills.slice(0, 4).join(', ')}
                        </p>
                    ) : null}
                    {job.match?.redFlags.length ? (
                        <div className="mt-2 flex flex-wrap gap-2">
                            {job.match.redFlags.slice(0, 3).map((flag) => (
                                <Tag color="red" icon={<ExclamationCircleOutlined />} key={flag}>{flag}</Tag>
                            ))}
                        </div>
                    ) : null}
                    {job.skills.length > 0 && (
                        <div className="mt-3 flex flex-wrap gap-2">
                            {job.skills.slice(0, 8).map((skill) => (
                                <Tag key={skill}>{skill}</Tag>
                            ))}
                        </div>
                    )}
                </div>
                <div className="flex shrink-0 gap-2">
                    <Button icon={<AimOutlined />} onClick={() => onAnalyze?.(job)} loading={loading}>
                        Analyze
                    </Button>
                    <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => onTrack?.(job)}>
                        Track
                    </Button>
                </div>
            </div>
        </div>
    );
}

export function MatchPanel({ match }: { match?: JobMatch | null }) {
    if (!match) {
        return (
            <div className="rounded-md border border-dashed border-slate-300 p-4 text-sm text-text-secondary dark:border-border-main">
                Analysis pending
            </div>
        );
    }

    return (
        <div className="rounded-md border border-slate-300 bg-surface p-4 dark:border-border-main">
            <div className="flex flex-col gap-4 md:flex-row md:items-center">
                <Progress type="circle" percent={match.matchScore} size={86} />
                <div className="flex-1">
                    <p className="m-0 text-sm text-text-secondary">{match.aiSummary}</p>
                    <div className="mt-4 grid gap-3 md:grid-cols-3">
                        <SkillGroup title="Matched" color="green" values={match.matchedSkills} emptyText="No clear overlap yet" />
                        <SkillGroup title="Needs but missing" color="orange" values={match.missingSkills} emptyText="No missing skills found" />
                        <SkillGroup title="Red flags" color="red" values={match.redFlags} emptyText="No warning tags" icon />
                    </div>
                </div>
            </div>
        </div>
    );
}

function SkillGroup({
    title,
    values,
    color,
    emptyText,
    icon,
}: {
    title: string;
    values: string[];
    color: 'green' | 'orange' | 'red';
    emptyText: string;
    icon?: boolean;
}) {
    return (
        <div>
            <h4 className="m-0 text-xs font-semibold uppercase text-text-secondary">{title}</h4>
            <div className="mt-2 flex flex-wrap gap-2">
                {values.length === 0 ? (
                    <span className="text-xs text-text-secondary">{emptyText}</span>
                ) : values.map((value) => (
                    <Tag color={color} icon={icon ? <ExclamationCircleOutlined /> : undefined} key={value}>{value}</Tag>
                ))}
            </div>
        </div>
    );
}

export function TrackingSelect({
    value,
    onChange,
    loading,
}: {
    value?: TrackingStatus;
    onChange: (value: TrackingStatus) => void;
    loading?: boolean;
}) {
    return (
        <Select
            className="min-w-40"
            value={value || 'INTERESTED'}
            loading={loading}
            onChange={onChange}
            options={TRACKING_COLUMNS.map((item) => ({ value: item.key, label: item.label }))}
        />
    );
}

export function DetailLink({ jobId }: { jobId: number }) {
    return (
        <Link href={`/jobjab/jobs/${jobId}`}>
            <Button type="link" icon={<ArrowRightOutlined />} iconPosition="end">
                Open
            </Button>
        </Link>
    );
}
