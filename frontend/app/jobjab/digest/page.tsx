'use client';

import { CalendarOutlined, ClockCircleOutlined, ExclamationCircleOutlined, ReloadOutlined, StarOutlined } from '@ant-design/icons';
import { Button, Empty, List, Skeleton, Tag } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { DetailLink, formatDuration } from '@/components/Jobjab';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { TITLE } from '@/constants/Title';
import type { WeeklyDigest, WeeklyDigestItem } from '@/interface/Jobjab';
import { useNotification } from '@/providers/NotificationProvider';
import { useSessionStore } from '@/stores/sessionStore';
import { fetchApi } from '@/utils/api';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { useChangeSubSideBar } from '@/utils/subSideBarUtil';

export default function JobjabDigestPage() {
    const notification = useNotification();
    const currentUserId = useSessionStore((s) => s.internalUserId);
    const [digests, setDigests] = useState<WeeklyDigest[]>([]);
    const [loading, setLoading] = useState(true);
    const [generating, setGenerating] = useState(false);

    useChangeTitle(TITLE.JOBJAB, 'DIGEST');
    useChangeSubSideBar(null);

    const load = useCallback(async () => {
        if (currentUserId === null) return;
        setLoading(true);
        try {
            setDigests(await fetchApi<WeeklyDigest[]>(API_SANDBOX.JOBJAB_DIGEST_LIST));
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Failed to load weekly digests' });
        } finally {
            setLoading(false);
        }
    }, [currentUserId, notification]);

    useEffect(() => {
        void load();
    }, [load]);

    const generate = async () => {
        setGenerating(true);
        try {
            const digest = await fetchApi<WeeklyDigest>(API_SANDBOX.JOBJAB_DIGEST_GENERATE_WEEKLY);
            setDigests((prev) => [digest, ...prev.filter((item) => item.id !== digest.id)]);
            notification.success({ message: 'Weekly digest generated' });
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Failed to generate weekly digest' });
        } finally {
            setGenerating(false);
        }
    };

    if (loading) return <Skeleton active paragraph={{ rows: 10 }} />;

    return (
        <div className="mx-auto flex w-full max-w-5xl flex-col gap-5 pb-12">
            <div className="flex justify-end gap-2">
                <Button icon={<ReloadOutlined />} onClick={load}>Refresh</Button>
                <Button type="primary" icon={<CalendarOutlined />} onClick={generate} loading={generating}>
                    Generate Weekly Digest
                </Button>
            </div>

            {digests.length === 0 ? (
                <div className="rounded-md border border-slate-300 bg-surface p-10 dark:border-border-main">
                    <Empty description="No weekly digests yet" />
                </div>
            ) : (
                <div className="flex flex-col gap-5">
                    {digests.map((digest) => (
                        <div key={digest.id} className="rounded-md border border-slate-300 bg-surface p-5 shadow-sm dark:border-border-main">
                            <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
                                <div>
                                    <div className="flex flex-wrap items-center gap-2">
                                        <Tag icon={<CalendarOutlined />}>{digest.weekStart} - {digest.weekEnd}</Tag>
                                        <Tag color="blue">{digest.status}</Tag>
                                        {digest.sentAt && <Tag>Delivered {new Date(digest.sentAt).toLocaleString()}</Tag>}
                                    </div>
                                    <h3 className="mt-3 text-lg font-semibold">Weekly Digest</h3>
                                    <p className="m-0 text-sm text-text-secondary">{digest.summary}</p>
                                </div>
                                <Tag color="gold" icon={<StarOutlined />}>{digest.items.length} jobs</Tag>
                            </div>

                            <List
                                className="mt-4"
                                dataSource={digest.items}
                                renderItem={(item) => (
                                    <List.Item actions={[<DetailLink key="open" jobId={item.jobId} />]}>
                                        <List.Item.Meta
                                            title={`${item.rank}. ${item.job.title}`}
                                            description={digestItemDescription(item)}
                                        />
                                        <div className="flex flex-wrap justify-end gap-2">
                                            {item.job.route?.durationSeconds != null && (
                                                <Tag color="cyan" icon={<ClockCircleOutlined />}>
                                                    {formatDuration(item.job.route.durationSeconds)}
                                                </Tag>
                                            )}
                                            {item.job.match?.redFlags.slice(0, 2).map((flag) => (
                                                <Tag color="red" icon={<ExclamationCircleOutlined />} key={flag}>{flag}</Tag>
                                            ))}
                                            {item.job.match && <Tag color="green">{item.job.match.matchScore}% match</Tag>}
                                        </div>
                                    </List.Item>
                                )}
                            />
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

function digestItemDescription(item: WeeklyDigestItem) {
    return [
        item.job.company,
        item.job.locationText || 'Location not listed',
        item.reason || 'Matched by JOBJAB',
    ]
        .filter(Boolean)
        .join(' - ');
}
