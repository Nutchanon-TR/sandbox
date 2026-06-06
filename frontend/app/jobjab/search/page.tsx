'use client';

import { DeleteOutlined, PlusOutlined, PlayCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Checkbox, Empty, Form, Input, InputNumber, List, Select, Skeleton, Tag } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { TITLE } from '@/constants/Title';
import type { JobSource, SearchRun, SearchRunEvent, SearchRunRequest } from '@/interface/Jobjab';
import { useNotification } from '@/providers/NotificationProvider';
import { useSessionStore } from '@/stores/sessionStore';
import { fetchApi } from '@/utils/api';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { useChangeSubSideBar } from '@/utils/subSideBarUtil';

export default function JobjabSearchPage() {
    const [form] = Form.useForm<SearchRunRequest>();
    const notification = useNotification();
    const currentUserId = useSessionStore((s) => s.internalUserId);
    const [runs, setRuns] = useState<SearchRun[]>([]);
    const [events, setEvents] = useState<SearchRunEvent[]>([]);
    const [sources, setSources] = useState<JobSource[]>([]);
    const [selectedRun, setSelectedRun] = useState<SearchRun | null>(null);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const activePollRef = useRef<number | null>(null);

    useChangeTitle(TITLE.JOBJAB, 'SEARCH');
    useChangeSubSideBar(null);

    const loadRuns = useCallback(async () => {
        if (currentUserId === null) return;
        setLoading(true);
        try {
            const nextRuns = await fetchApi<SearchRun[]>(API_SANDBOX.JOBJAB_SEARCH_RUN_LIST);
            const nextSources = await fetchApi<JobSource[]>(API_SANDBOX.JOBJAB_SOURCE_LIST);
            setSources(nextSources);
            const readySources = defaultSourceKeys(nextSources);
            if (!form.getFieldValue('sourceKeys')?.length && readySources.length > 0) {
                form.setFieldValue('sourceKeys', readySources);
            }
            setRuns(nextRuns);
            if (nextRuns.length > 0) {
                setSelectedRun(nextRuns[0]);
                const nextEvents = await fetchApi<SearchRunEvent[]>(
                    API_SANDBOX.JOBJAB_SEARCH_RUN_EVENTS,
                    {},
                    { runId: nextRuns[0].id },
                );
                setEvents(nextEvents);
            }
        } finally {
            setLoading(false);
        }
    }, [currentUserId, form]);

    const sourceOptions = sources.map((source) => ({
        value: source.sourceKey,
        disabled: !source.searchable,
        label: (
            <span className="inline-flex items-center gap-2">
                {source.displayName}
                {!source.enabled && <Tag>Disabled</Tag>}
                {source.requiresPartnerApproval && <Tag color="purple">Approval</Tag>}
                {source.requiresJobUrl && <Tag>URL</Tag>}
                {!source.configured && source.acceptsPerSearchTargets && <Tag color="blue">Target</Tag>}
                {!source.configured && !source.acceptsPerSearchTargets && <Tag color="gold">Needs config</Tag>}
                {!source.registered && <Tag color="red">No adapter</Tag>}
            </span>
        ),
    }));
    const sourceByKey = useMemo(() => new Map(sources.map((source) => [source.sourceKey, source])), [sources]);

    useEffect(() => {
        void loadRuns();
    }, [loadRuns]);

    useEffect(() => {
        return () => {
            activePollRef.current = null;
        };
    }, []);

    const fetchRunEvents = useCallback(async (runId: number) => {
        const [run, nextEvents] = await Promise.all([
            fetchApi<SearchRun>(API_SANDBOX.JOBJAB_SEARCH_RUN_GET, {}, { runId }),
            fetchApi<SearchRunEvent[]>(API_SANDBOX.JOBJAB_SEARCH_RUN_EVENTS, {}, { runId }),
        ]);
        setSelectedRun(run);
        setEvents(nextEvents);
        setRuns((prev) => [run, ...prev.filter((item) => item.id !== run.id)]);
        return run;
    }, []);

    const pollRun = useCallback(async (runId: number) => {
        activePollRef.current = runId;
        for (let attempt = 0; attempt < 120 && activePollRef.current === runId; attempt++) {
            const run = await fetchRunEvents(runId);
            if (run.status === 'COMPLETED' || run.status === 'PARTIAL') {
                notification.success({ message: run.status === 'PARTIAL' ? 'JOBJAB search completed with warnings' : 'JOBJAB search completed' });
                return;
            }
            if (run.status === 'FAILED') {
                notification.error({ message: 'JOBJAB search failed' });
                return;
            }
            await new Promise((resolve) => setTimeout(resolve, 1500));
        }
    }, [fetchRunEvents, notification]);

    useEffect(() => {
        if (!selectedRun || !isRunActive(selectedRun.status) || activePollRef.current === selectedRun.id) return;
        void pollRun(selectedRun.id);
    }, [selectedRun, pollRun]);

    const startSearch = async (values: SearchRunRequest) => {
        setSubmitting(true);
        try {
            const request = cleanSearchRequest(values, sources);
            if (!request.sourceKeys?.length) {
                notification.warning({ message: 'Select at least one ready source' });
                return;
            }
            const run = await fetchApi<SearchRun>(API_SANDBOX.JOBJAB_SEARCH_RUN_CREATE, request);
            const reusedActiveRun = runs.some((item) => item.id === run.id && isRunActive(item.status));
            setSelectedRun(run);
            setEvents([]);
            setRuns((prev) => [run, ...prev.filter((item) => item.id !== run.id)]);
            notification.info({ message: reusedActiveRun ? 'Existing JOBJAB search resumed' : 'JOBJAB search started' });
            void pollRun(run.id);
        } catch (error) {
            console.error(error);
            notification.error({ message: 'JOBJAB search failed' });
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) {
        return <Skeleton active paragraph={{ rows: 8 }} />;
    }

    return (
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-5 pb-12">
            <div className="rounded-md border border-slate-300 bg-surface p-5 shadow-sm dark:border-border-main">
                <Form
                    form={form}
                    layout="vertical"
                    initialValues={{
                        limit: 10,
                        jobUrls: [],
                        manualJobs: [],
                        sourceTargets: { greenhouse: [], lever: [], ashby: [], sitemap: [] },
                        sourceKeys: defaultSourceKeys(sources),
                    }}
                    onFinish={startSearch}
                >
                    <div className="grid gap-4 md:grid-cols-2">
                        <Form.Item label="Query" name="query">
                            <Input placeholder="Frontend Developer" />
                        </Form.Item>
                        <Form.Item label="Location" name="locationText">
                            <Input placeholder="Bangkok, BTS Asok" />
                        </Form.Item>
                        <Form.Item label="Job URLs" name="jobUrls">
                            <Select mode="tags" tokenSeparators={[',', '\n']} placeholder="https://..." />
                        </Form.Item>
                        <Form.Item label="Result count" name="limit">
                            <InputNumber min={1} max={50} className="w-full" />
                        </Form.Item>
                    </div>
                    <div className="grid gap-4 md:grid-cols-4">
                        <Form.Item label={sourceByKey.get('greenhouse')?.targetHint || 'Greenhouse boards'} name={['sourceTargets', 'greenhouse']}>
                            <Select mode="tags" tokenSeparators={[',', '\n']} placeholder={sourcePlaceholder(sourceByKey.get('greenhouse'), 'company-token')} />
                        </Form.Item>
                        <Form.Item label={sourceByKey.get('lever')?.targetHint || 'Lever companies'} name={['sourceTargets', 'lever']}>
                            <Select mode="tags" tokenSeparators={[',', '\n']} placeholder={sourcePlaceholder(sourceByKey.get('lever'), 'company-site')} />
                        </Form.Item>
                        <Form.Item label={sourceByKey.get('ashby')?.targetHint || 'Ashby boards'} name={['sourceTargets', 'ashby']}>
                            <Select mode="tags" tokenSeparators={[',', '\n']} placeholder={sourcePlaceholder(sourceByKey.get('ashby'), 'job-board-name')} />
                        </Form.Item>
                        <Form.Item label={sourceByKey.get('sitemap')?.targetHint || 'Sitemap URLs'} name={['sourceTargets', 'sitemap']}>
                            <Select mode="tags" tokenSeparators={[',', '\n']} placeholder={sourcePlaceholder(sourceByKey.get('sitemap'), 'https://example.com/sitemap.xml')} />
                        </Form.Item>
                    </div>
                    <Form.Item
                        label="Sources"
                        name="sourceKeys"
                        rules={[{ required: true, type: 'array', min: 1, message: 'Select at least one source' }]}
                    >
                        <Checkbox.Group options={sourceOptions} />
                    </Form.Item>
                    <Form.List name="manualJobs">
                        {(fields, { add, remove }) => (
                            <div className="mb-4">
                                <div className="mb-3 flex items-center justify-between">
                                    <h3 className="m-0 text-base font-semibold">Manual JD Paste</h3>
                                    <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({ currency: 'THB', skills: [] })}>
                                        Add Job
                                    </Button>
                                </div>
                                <div className="flex flex-col gap-4">
                                    {fields.map(({ key, name, ...restField }) => (
                                        <div key={key} className="rounded-md border border-dashed border-slate-300 p-4 dark:border-border-main">
                                            <div className="grid gap-4 md:grid-cols-2">
                                                <Form.Item {...restField} label="Title" name={[name, 'title']}>
                                                    <Input placeholder="Frontend Engineer" />
                                                </Form.Item>
                                                <Form.Item {...restField} label="Company" name={[name, 'company']}>
                                                    <Input placeholder="Company name" />
                                                </Form.Item>
                                                <Form.Item {...restField} label="Location" name={[name, 'locationText']}>
                                                    <Input placeholder="Bangkok / Remote / Hybrid" />
                                                </Form.Item>
                                                <Form.Item {...restField} label="Apply URL" name={[name, 'applyUrl']}>
                                                    <Input placeholder="https://..." />
                                                </Form.Item>
                                                <Form.Item {...restField} label="Skills" name={[name, 'skills']}>
                                                    <Select mode="tags" tokenSeparators={[',']} placeholder="React, TypeScript" />
                                                </Form.Item>
                                                <Form.Item {...restField} label="Currency" name={[name, 'currency']}>
                                                    <Input placeholder="THB" />
                                                </Form.Item>
                                            </div>
                                            <Form.Item {...restField} label="Job Description" name={[name, 'description']}>
                                                <Input.TextArea rows={5} placeholder="Paste JD text from any platform" />
                                            </Form.Item>
                                            <div className="flex justify-end">
                                                <Button type="text" danger icon={<DeleteOutlined />} onClick={() => remove(name)}>
                                                    Remove
                                                </Button>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </Form.List>
                    {sources.some((source) => !source.searchable) && (
                        <Alert
                            className="mb-4"
                            type="info"
                            showIcon
                            message="Some sources need platform configuration before search"
                            description={sources
                                .filter((source) => !source.searchable)
                                .map((source) => `${source.displayName}: ${source.guidance || 'Not ready'}`)
                                .join(' | ')}
                        />
                    )}
                    <div className="flex justify-end">
                        <Button htmlType="submit" type="primary" icon={<PlayCircleOutlined />} loading={submitting}>
                            Run Search
                        </Button>
                    </div>
                </Form>
            </div>

            <div className="grid gap-5 lg:grid-cols-[320px_1fr]">
                <div className="rounded-md border border-slate-300 bg-surface p-4 dark:border-border-main">
                    <div className="mb-3 flex items-center justify-between">
                        <h3 className="m-0 text-base font-semibold">Runs</h3>
                        <Button type="text" icon={<ReloadOutlined />} onClick={loadRuns} />
                    </div>
                    {runs.length === 0 ? (
                        <Empty description="No search runs" />
                    ) : (
                        <List
                            dataSource={runs}
                            renderItem={(run) => (
                                <List.Item
                                    className="cursor-pointer rounded-md px-2"
                                    onClick={async () => {
                                        await fetchRunEvents(run.id);
                                    }}
                                >
                                    <List.Item.Meta
                                        title={<span>{run.query || 'Untitled search'}</span>}
                                        description={`${run.status} at ${run.locationText || 'Any location'}`}
                                    />
                                </List.Item>
                            )}
                        />
                    )}
                </div>

                <div className="rounded-md border border-slate-300 bg-surface p-4 dark:border-border-main">
                    <div className="mb-3 flex items-center justify-between">
                        <h3 className="m-0 text-base font-semibold">Live Log</h3>
                        {selectedRun && <Tag color={runStatusColor(selectedRun.status)}>{selectedRun.status}</Tag>}
                    </div>
                    {events.length === 0 ? (
                        <Empty description="No events" />
                    ) : (
                        <List
                            dataSource={events}
                            renderItem={(event) => {
                                const matchAnalysis = typeof event.payload?.matchAnalysis === 'string' ? event.payload.matchAnalysis : undefined;
                                return (
                                    <List.Item>
                                        <List.Item.Meta
                                            title={<span>{event.sourceKey ? `[${event.sourceKey}] ` : ''}{event.message}</span>}
                                            description={new Date(event.createdAt).toLocaleString()}
                                        />
                                        <div className="flex flex-wrap justify-end gap-2">
                                            {matchAnalysis && (
                                                <Tag color={matchAnalysis === 'AUTO' ? 'green' : 'default'}>
                                                    AI {matchAnalysis === 'AUTO' ? 'AUTO' : 'CAPPED'}
                                                </Tag>
                                            )}
                                            <Tag color={event.level === 'ERROR' ? 'red' : event.level === 'WARN' ? 'gold' : 'blue'}>{event.level}</Tag>
                                        </div>
                                    </List.Item>
                                );
                            }}
                        />
                    )}
                </div>
            </div>
        </div>
    );
}

function cleanSearchRequest(values: SearchRunRequest, sources: JobSource[]): SearchRunRequest {
    const searchableKeys = new Set(sources.filter((source) => source.searchable).map((source) => source.sourceKey));
    const jobUrls = cleanList(values.jobUrls);
    const manualJobs = cleanManualJobs(values.manualJobs);
    const sourceTargets: Record<string, string[]> = Object.fromEntries(
        Object.entries(values.sourceTargets || {})
            .map(([key, targets]) => [key, cleanList(targets)])
            .filter(([, targets]) => (targets as string[]).length > 0),
    );
    const sourceKeys = new Set(cleanList(values.sourceKeys).filter((sourceKey) => searchableKeys.has(sourceKey)));
    Object.keys(sourceTargets)
        .filter((sourceKey) => searchableKeys.has(sourceKey))
        .forEach((sourceKey) => sourceKeys.add(sourceKey));
    inferredSourceKeys(jobUrls)
        .filter((sourceKey) => searchableKeys.has(sourceKey))
        .forEach((sourceKey) => sourceKeys.add(sourceKey));
    if (manualJobs.length > 0 && searchableKeys.has('manual_jd')) sourceKeys.add('manual_jd');
    if (jobUrls.length > 0) {
        if (searchableKeys.has('user_url')) sourceKeys.add('user_url');
        if (searchableKeys.has('structured_data')) sourceKeys.add('structured_data');
    }
    return {
        ...values,
        jobUrls,
        manualJobs,
        sourceTargets,
        sourceKeys: Array.from(sourceKeys),
    };
}

function defaultSourceKeys(sources: JobSource[]) {
    return sources
        .filter((source) => source.searchable)
        .filter((source) => source.configured || !source.acceptsPerSearchTargets)
        .map((source) => source.sourceKey);
}

function inferredSourceKeys(urls: string[]) {
    const keys = new Set<string>();
    urls.forEach((url) => {
        try {
            const host = new URL(url).hostname.toLowerCase();
            if (host === 'boards.greenhouse.io' || host === 'job-boards.greenhouse.io' || host === 'boards-api.greenhouse.io') {
                keys.add('greenhouse');
            }
            if (host === 'jobs.lever.co' || host === 'api.lever.co') {
                keys.add('lever');
            }
            if (host === 'jobs.ashbyhq.com' || host === 'api.ashbyhq.com') {
                keys.add('ashby');
            }
        } catch {
            // Ignore invalid URLs; backend adapters will validate the remaining input.
        }
    });
    return Array.from(keys);
}

function cleanManualJobs(values?: SearchRunRequest['manualJobs']) {
    return (values || [])
        .map((job) => ({
            ...job,
            title: cleanText(job.title),
            company: cleanText(job.company),
            locationText: cleanText(job.locationText),
            currency: cleanText(job.currency) || 'THB',
            employmentType: cleanText(job.employmentType),
            workplaceType: cleanText(job.workplaceType),
            applyUrl: cleanText(job.applyUrl),
            description: cleanText(job.description),
            skills: cleanList(job.skills),
        }))
        .filter((job) => Boolean(job.title || job.description || job.applyUrl));
}

function cleanText(value?: string) {
    const next = value?.trim();
    return next || undefined;
}

function sourcePlaceholder(source: JobSource | undefined, fallback: string) {
    if (!source?.targetExample) return fallback;
    return source.targetHint ? `${source.targetHint}: ${source.targetExample}` : source.targetExample;
}

function runStatusColor(status: string) {
    if (status === 'COMPLETED') return 'green';
    if (status === 'PARTIAL') return 'gold';
    if (status === 'FAILED') return 'red';
    return 'blue';
}

function isRunActive(status: string) {
    return status !== 'COMPLETED' && status !== 'PARTIAL' && status !== 'FAILED';
}

function cleanList(values?: string[]) {
    return (values || [])
        .filter((value) => value !== null && value !== undefined && String(value).trim().length > 0)
        .map((value) => String(value).trim())
        .filter((value, index, list) => list.indexOf(value) === index);
}
