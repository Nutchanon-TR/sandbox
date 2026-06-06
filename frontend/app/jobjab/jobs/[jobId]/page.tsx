'use client';

import { ArrowLeftOutlined, EnvironmentOutlined, LinkOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { Button, Descriptions, Divider, Empty, Select, Skeleton, Space, Tag } from 'antd';
import { useParams, useRouter } from 'next/navigation';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { formatDistance, formatDuration, formatSalary, MatchPanel, TrackingSelect } from '@/components/Jobjab';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { TITLE } from '@/constants/Title';
import type { Job, JobMatch, JobTracking, RouteRequest, RouteResult, TrackingStatus } from '@/interface/Jobjab';
import { useNotification } from '@/providers/NotificationProvider';
import { useSessionStore } from '@/stores/sessionStore';
import { fetchApi } from '@/utils/api';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { useChangeSubSideBar } from '@/utils/subSideBarUtil';

export default function JobjabJobDetailPage() {
    const params = useParams<{ jobId: string }>();
    const router = useRouter();
    const notification = useNotification();
    const currentUserId = useSessionStore((s) => s.internalUserId);
    const jobId = useMemo(() => Number(params.jobId), [params.jobId]);
    const [job, setJob] = useState<Job | null>(null);
    const [route, setRoute] = useState<RouteResult | null>(null);
    const [loading, setLoading] = useState(true);
    const [analyzing, setAnalyzing] = useState(false);
    const [routing, setRouting] = useState(false);
    const [tracking, setTracking] = useState(false);
    const [travelMode, setTravelMode] = useState<string | undefined>();

    useChangeTitle(TITLE.JOBJAB, 'JOBS');
    useChangeSubSideBar(null);

    const loadJob = useCallback(async () => {
        if (currentUserId === null || !Number.isFinite(jobId)) return;
        setLoading(true);
        try {
            const nextJob = await fetchApi<Job>(API_SANDBOX.JOBJAB_JOB_DETAIL, {}, { jobId });
            setJob(nextJob);
            setRoute(nextJob.route || null);
            setTravelMode(nextJob.route?.travelMode);
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Failed to load job detail' });
        } finally {
            setLoading(false);
        }
    }, [currentUserId, jobId, notification]);

    useEffect(() => {
        void loadJob();
    }, [loadJob]);

    const analyze = async () => {
        if (!job) return;
        setAnalyzing(true);
        try {
            const match = await fetchApi<JobMatch>(API_SANDBOX.JOBJAB_JOB_ANALYZE, {}, { jobId: job.id });
            setJob({ ...job, match });
        } finally {
            setAnalyzing(false);
        }
    };

    const updateTracking = async (status: TrackingStatus) => {
        if (!job) return;
        setTracking(true);
        try {
            const nextTracking = await fetchApi<JobTracking>(
                API_SANDBOX.JOBJAB_TRACKING_UPDATE,
                { status, notes: job.tracking?.notes || '' },
                { jobId: job.id },
            );
            setJob({ ...job, tracking: nextTracking });
        } finally {
            setTracking(false);
        }
    };

    const computeRoute = async () => {
        if (!job) return;
        setRouting(true);
        try {
            const nextRoute = await fetchApi<RouteResult>(
                API_SANDBOX.JOBJAB_ROUTE_COMPUTE,
                routeRequest(travelMode),
                { jobId: job.id },
            );
            setRoute(nextRoute);
            setTravelMode(nextRoute.travelMode);
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Route calculation needs profile and home/work location' });
        } finally {
            setRouting(false);
        }
    };

    if (loading) return <Skeleton active paragraph={{ rows: 10 }} />;
    if (!job) return <Empty description="Job not found" />;

    return (
        <div className="mx-auto flex w-full max-w-5xl flex-col gap-5 pb-12">
            <div className="flex items-center justify-between">
                <Button icon={<ArrowLeftOutlined />} onClick={() => router.push('/jobjab/jobs')}>
                    Back
                </Button>
                <Space>
                    <TrackingSelect value={job.tracking?.status} onChange={updateTracking} loading={tracking} />
                    <Button type="primary" icon={<ThunderboltOutlined />} onClick={analyze} loading={analyzing}>
                        Analyze
                    </Button>
                </Space>
            </div>

            <div className="rounded-md border border-slate-300 bg-surface p-5 shadow-sm dark:border-border-main">
                <div className="flex flex-col gap-4 md:flex-row md:justify-between">
                    <div>
                        <h2 className="m-0 text-2xl font-semibold">{job.title}</h2>
                        <p className="mt-2 text-sm text-text-secondary">{job.company} - {job.locationText || 'Location not listed'}</p>
                        <div className="mt-3 flex flex-wrap gap-2">
                            {job.skills.map((skill) => <Tag key={skill}>{skill}</Tag>)}
                        </div>
                    </div>
                    {job.applyUrl && (
                        <a href={job.applyUrl} target="_blank" rel="noreferrer">
                            <Button icon={<LinkOutlined />}>Apply</Button>
                        </a>
                    )}
                </div>

                <Divider />
                <Descriptions column={{ xs: 1, md: 2 }} size="small">
                    <Descriptions.Item label="Salary">{formatSalary(job)}</Descriptions.Item>
                    <Descriptions.Item label="Employment">{job.employmentType || 'N/A'}</Descriptions.Item>
                    <Descriptions.Item label="Workplace">{job.workplaceType || 'N/A'}</Descriptions.Item>
                    <Descriptions.Item label="Last seen">{new Date(job.lastSeenAt).toLocaleString()}</Descriptions.Item>
                </Descriptions>
            </div>

            <MatchPanel match={job.match} />

            <div className="rounded-md border border-slate-300 bg-surface p-5 dark:border-border-main">
                <div className="mb-3 flex items-center justify-between">
                    <h3 className="m-0 text-base font-semibold">Route</h3>
                    <Space wrap>
                        <Select
                            allowClear
                            className="min-w-36"
                            placeholder="Profile default"
                            value={travelMode}
                            onChange={setTravelMode}
                            options={TRAVEL_MODE_OPTIONS}
                        />
                        <Button icon={<EnvironmentOutlined />} onClick={computeRoute} loading={routing}>
                            Calculate
                        </Button>
                    </Space>
                </div>
                {route ? (
                    <Descriptions column={{ xs: 1, md: 4 }} size="small">
                        <Descriptions.Item label="Mode">{route.travelMode}</Descriptions.Item>
                        <Descriptions.Item label="Distance">{formatDistance(route.distanceMeters)}</Descriptions.Item>
                        <Descriptions.Item label="Time">{formatDuration(route.durationSeconds)}</Descriptions.Item>
                        <Descriptions.Item label="Provider">{route.provider}</Descriptions.Item>
                    </Descriptions>
                ) : (
                    <p className="m-0 text-sm text-text-secondary">Route not calculated</p>
                )}
            </div>

            <div className="rounded-md border border-slate-300 bg-surface p-5 dark:border-border-main">
                <h3 className="m-0 text-base font-semibold">Description</h3>
                <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-text-secondary">{job.description}</p>
            </div>
        </div>
    );
}

const TRAVEL_MODE_OPTIONS = [
    { value: 'DRIVE', label: 'Drive' },
    { value: 'TRANSIT', label: 'Transit' },
    { value: 'WALK', label: 'Walk' },
    { value: 'TWO_WHEELER', label: 'Two wheeler' },
];

function routeRequest(travelMode?: string): RouteRequest {
    return travelMode ? { travelMode } : {};
}
