'use client';

import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Empty, Input, Skeleton, Switch } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { JobSummaryCard } from '@/components/Jobjab';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { TITLE } from '@/constants/Title';
import type { Job, JobMatch, JobTracking, PageResponse } from '@/interface/Jobjab';
import { useNotification } from '@/providers/NotificationProvider';
import { useSessionStore } from '@/stores/sessionStore';
import { fetchApi } from '@/utils/api';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { useChangeSubSideBar } from '@/utils/subSideBarUtil';

export default function JobjabJobsPage() {
    const notification = useNotification();
    const currentUserId = useSessionStore((s) => s.internalUserId);
    const [jobs, setJobs] = useState<Job[]>([]);
    const [q, setQ] = useState('');
    const [nearbyFirst, setNearbyFirst] = useState(false);
    const [loading, setLoading] = useState(true);
    const [analyzingId, setAnalyzingId] = useState<number | null>(null);

    useChangeTitle(TITLE.JOBJAB, 'JOBS');
    useChangeSubSideBar(null);

    const loadJobs = useCallback(async (query = q, nextNearbyFirst = nearbyFirst) => {
        if (currentUserId === null) return;
        setLoading(true);
        try {
            const page = await fetchApi<PageResponse<Job>>(API_SANDBOX.JOBJAB_JOB_LIST, { q: query, limit: 30, nearbyFirst: nextNearbyFirst });
            setJobs(page.items);
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Failed to load JOBJAB jobs' });
        } finally {
            setLoading(false);
        }
    }, [currentUserId, notification, q, nearbyFirst]);

    useEffect(() => {
        void loadJobs('');
    }, [loadJobs]);

    const analyze = async (job: Job) => {
        setAnalyzingId(job.id);
        try {
            const match = await fetchApi<JobMatch>(API_SANDBOX.JOBJAB_JOB_ANALYZE, {}, { jobId: job.id });
            setJobs((prev) => prev.map((item) => item.id === job.id ? { ...item, match } : item));
        } finally {
            setAnalyzingId(null);
        }
    };

    const track = async (job: Job) => {
        const tracking = await fetchApi<JobTracking>(
            API_SANDBOX.JOBJAB_TRACKING_UPDATE,
            { status: job.tracking?.status || 'INTERESTED', notes: job.tracking?.notes || '' },
            { jobId: job.id },
        );
        setJobs((prev) => prev.map((item) => item.id === job.id ? { ...item, tracking } : item));
        notification.success({ message: 'Job added to tracking' });
    };

    return (
        <div className="mx-auto flex w-full max-w-5xl flex-col gap-5 pb-12">
            <div className="rounded-md border border-slate-300 bg-surface p-4 shadow-sm dark:border-border-main">
                <div className="flex flex-col gap-3 md:flex-row md:items-center">
                    <Input
                        value={q}
                        onChange={(event) => setQ(event.target.value)}
                        onPressEnter={() => loadJobs(q)}
                        prefix={<SearchOutlined />}
                        placeholder="Search jobs"
                    />
                    <div className="flex gap-2">
                        <Button type="primary" icon={<SearchOutlined />} onClick={() => loadJobs(q)}>
                            Search
                        </Button>
                        <Button icon={<ReloadOutlined />} onClick={() => loadJobs('')}>
                            Refresh
                        </Button>
                    </div>
                    <div className="flex items-center gap-2 text-sm text-text-secondary">
                        <Switch
                            checked={nearbyFirst}
                            onChange={(checked) => {
                                setNearbyFirst(checked);
                                void loadJobs(q, checked);
                            }}
                        />
                        Nearby first
                    </div>
                </div>
            </div>

            {loading ? (
                <Skeleton active paragraph={{ rows: 8 }} />
            ) : jobs.length === 0 ? (
                <div className="rounded-md border border-slate-300 bg-surface p-10 dark:border-border-main">
                    <Empty description="No jobs yet" />
                </div>
            ) : (
                <div className="flex flex-col gap-4">
                    {jobs.map((job) => (
                        <JobSummaryCard
                            key={job.id}
                            job={job}
                            onAnalyze={analyze}
                            onTrack={track}
                            loading={analyzingId === job.id}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}
