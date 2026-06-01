'use client';

import { ReloadOutlined } from '@ant-design/icons';
import { Button, Empty, Skeleton, Tag } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { DetailLink, formatSalary, TRACKING_COLUMNS } from '@/components/Jobjab';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { TITLE } from '@/constants/Title';
import type { Job, JobTracking, PageResponse, TrackingStatus } from '@/interface/Jobjab';
import { useNotification } from '@/providers/NotificationProvider';
import { useSessionStore } from '@/stores/sessionStore';
import { fetchApi } from '@/utils/api';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { useChangeSubSideBar } from '@/utils/subSideBarUtil';

export default function JobjabTrackingPage() {
    const notification = useNotification();
    const currentUserId = useSessionStore((s) => s.internalUserId);
    const [jobs, setJobs] = useState<Job[]>([]);
    const [tracking, setTracking] = useState<JobTracking[]>([]);
    const [loading, setLoading] = useState(true);
    const [draggingJobId, setDraggingJobId] = useState<number | null>(null);

    useChangeTitle(TITLE.JOBJAB, 'TRACKING');
    useChangeSubSideBar(null);

    const load = useCallback(async () => {
        if (currentUserId === null) return;
        setLoading(true);
        try {
            const [jobPage, trackingRows] = await Promise.all([
                fetchApi<PageResponse<Job>>(API_SANDBOX.JOBJAB_JOB_LIST, { limit: 500, trackedOnly: true }),
                fetchApi<JobTracking[]>(API_SANDBOX.JOBJAB_TRACKING_LIST),
            ]);
            setJobs(jobPage.items);
            setTracking(trackingRows);
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Failed to load tracking board' });
        } finally {
            setLoading(false);
        }
    }, [currentUserId, notification]);

    useEffect(() => {
        void load();
    }, [load]);

    const jobsById = useMemo(() => new Map(jobs.map((job) => [job.id, job])), [jobs]);
    const trackedJobs = useMemo(() => tracking.map((row) => ({ row, job: jobsById.get(row.jobId) })).filter((item) => item.job), [jobsById, tracking]);

    const move = async (jobId: number, status: TrackingStatus) => {
        const next = await fetchApi<JobTracking>(API_SANDBOX.JOBJAB_TRACKING_UPDATE, { status }, { jobId });
        setTracking((prev) => prev.map((row) => row.jobId === jobId ? next : row));
    };

    const handleDrop = async (status: TrackingStatus) => {
        if (draggingJobId === null) return;
        const current = tracking.find((row) => row.jobId === draggingJobId);
        setDraggingJobId(null);
        if (!current || current.status === status) return;
        try {
            await move(draggingJobId, status);
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Failed to move job' });
        }
    };

    if (loading) return <Skeleton active paragraph={{ rows: 10 }} />;

    return (
        <div className="flex h-full min-h-0 flex-col gap-4 pb-8">
            <div className="flex justify-end">
                <Button icon={<ReloadOutlined />} onClick={load}>Refresh</Button>
            </div>
            {trackedJobs.length === 0 ? (
                <div className="rounded-md border border-slate-300 bg-surface p-10 dark:border-border-main">
                    <Empty description="No tracked jobs" />
                </div>
            ) : (
                <div className="grid min-h-0 gap-4 overflow-x-auto pb-2 xl:grid-cols-6">
                    {TRACKING_COLUMNS.map((column) => {
                        const items = trackedJobs.filter((item) => item.row.status === column.key);
                        return (
                            <div
                                key={column.key}
                                className={`min-w-64 rounded-md border bg-surface p-3 transition-colors dark:border-border-main ${
                                    draggingJobId !== null ? 'border-blue-300' : 'border-slate-300'
                                }`}
                                onDragOver={(event) => event.preventDefault()}
                                onDrop={() => handleDrop(column.key)}
                            >
                                <div className="mb-3 flex items-center justify-between">
                                    <h3 className="m-0 text-sm font-semibold">{column.label}</h3>
                                    <Tag>{items.length}</Tag>
                                </div>
                                <div className="flex flex-col gap-3">
                                    {items.map(({ row, job }) => job && (
                                        <div
                                            key={row.id}
                                            draggable
                                            onDragStart={() => setDraggingJobId(job.id)}
                                            onDragEnd={() => setDraggingJobId(null)}
                                            className={`cursor-move rounded-md border border-slate-200 bg-background p-3 transition-opacity dark:border-border-main ${
                                                draggingJobId === job.id ? 'opacity-50' : 'opacity-100'
                                            }`}
                                        >
                                            <div className="text-sm font-semibold">{job.title}</div>
                                            <div className="mt-1 text-xs text-text-secondary">{job.company}</div>
                                            <div className="mt-1 text-xs text-text-secondary">{formatSalary(job)}</div>
                                            <div className="mt-3 flex flex-wrap gap-1">
                                                {TRACKING_COLUMNS.filter((next) => next.key !== row.status).slice(0, 3).map((next) => (
                                                    <Button key={next.key} size="small" onClick={() => move(job.id, next.key)}>
                                                        {next.label}
                                                    </Button>
                                                ))}
                                                <DetailLink jobId={job.id} />
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}
