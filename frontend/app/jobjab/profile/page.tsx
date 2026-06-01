'use client';

import { SaveOutlined } from '@ant-design/icons';
import { Button, Form, Input, InputNumber, Select, Skeleton, Switch } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { TITLE } from '@/constants/Title';
import type { JobjabProfile, JobjabProfilePayload } from '@/interface/Jobjab';
import { useNotification } from '@/providers/NotificationProvider';
import { useSessionStore } from '@/stores/sessionStore';
import { fetchApi } from '@/utils/api';
import { useChangeTitle } from '@/utils/breadCrumbUtil';
import { useChangeSubSideBar } from '@/utils/subSideBarUtil';

const DEFAULT_PROFILE: JobjabProfilePayload = {
    headline: '',
    desiredTitles: [],
    skills: [],
    experiences: [],
    preferredLocations: [],
    employmentTypes: [],
    salaryMin: undefined,
    salaryMax: undefined,
    homeLocationLabel: '',
    homeLatitude: undefined,
    homeLongitude: undefined,
    maxCommuteMinutes: 60,
    travelMode: 'DRIVE',
    weeklyDigestEnabled: true,
};

export default function JobjabProfilePage() {
    const [form] = Form.useForm<JobjabProfilePayload>();
    const notification = useNotification();
    const currentUserId = useSessionStore((s) => s.internalUserId);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);

    useChangeTitle(TITLE.JOBJAB, 'PROFILE');
    useChangeSubSideBar(null);

    const loadProfile = useCallback(async () => {
        if (currentUserId === null) return;
        setLoading(true);
        try {
            const profile = await fetchApi<JobjabProfile | null>(API_SANDBOX.JOBJAB_PROFILE);
            form.setFieldsValue(profile ? {
                headline: profile.headline,
                desiredTitles: profile.desiredTitles,
                skills: profile.skills,
                experiences: profile.experiences,
                preferredLocations: profile.preferredLocations,
                employmentTypes: profile.employmentTypes,
                salaryMin: profile.salaryMin,
                salaryMax: profile.salaryMax,
                homeLocationLabel: profile.homeLocationLabel,
                homeLatitude: profile.homeLatitude,
                homeLongitude: profile.homeLongitude,
                maxCommuteMinutes: profile.maxCommuteMinutes,
                travelMode: profile.travelMode,
                weeklyDigestEnabled: profile.weeklyDigestEnabled,
            } : DEFAULT_PROFILE);
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Failed to load JOBJAB profile' });
        } finally {
            setLoading(false);
        }
    }, [currentUserId, form, notification]);

    useEffect(() => {
        void loadProfile();
    }, [loadProfile]);

    const saveProfile = async (values: JobjabProfilePayload) => {
        setSaving(true);
        try {
            await fetchApi<JobjabProfile>(API_SANDBOX.JOBJAB_PROFILE_SAVE, {
                ...values,
                desiredTitles: values.desiredTitles || [],
                skills: values.skills || [],
                experiences: values.experiences || [],
                preferredLocations: values.preferredLocations || [],
                employmentTypes: values.employmentTypes || [],
            });
            notification.success({ message: 'JOBJAB profile saved' });
        } catch (error) {
            console.error(error);
            notification.error({ message: 'Failed to save JOBJAB profile' });
        } finally {
            setSaving(false);
        }
    };

    if (loading) return <Skeleton active paragraph={{ rows: 10 }} />;

    return (
        <div className="mx-auto w-full max-w-4xl rounded-md border border-slate-300 bg-surface p-5 shadow-sm dark:border-border-main">
            <Form form={form} layout="vertical" initialValues={DEFAULT_PROFILE} onFinish={saveProfile}>
                <Form.Item label="Headline" name="headline">
                    <Input placeholder="Frontend engineer focused on React and Spring Boot" />
                </Form.Item>

                <div className="grid gap-4 md:grid-cols-2">
                    <Form.Item label="Desired titles" name="desiredTitles">
                        <Select mode="tags" tokenSeparators={[',']} placeholder="Frontend Developer" />
                    </Form.Item>
                    <Form.Item label="Skills" name="skills">
                        <Select mode="tags" tokenSeparators={[',']} placeholder="React, TypeScript" />
                    </Form.Item>
                    <Form.Item label="Experience" name="experiences">
                        <Select mode="tags" tokenSeparators={[',']} placeholder="2 years React" />
                    </Form.Item>
                    <Form.Item label="Preferred locations" name="preferredLocations">
                        <Select mode="tags" tokenSeparators={[',']} placeholder="Bangkok, Remote" />
                    </Form.Item>
                    <Form.Item label="Employment types" name="employmentTypes">
                        <Select mode="tags" tokenSeparators={[',']} placeholder="Full-time" />
                    </Form.Item>
                    <Form.Item label="Travel mode" name="travelMode">
                        <Select
                            options={[
                                { value: 'DRIVE', label: 'Drive' },
                                { value: 'TRANSIT', label: 'Transit' },
                                { value: 'WALK', label: 'Walk' },
                                { value: 'TWO_WHEELER', label: 'Two wheeler' },
                            ]}
                        />
                    </Form.Item>
                    <Form.Item label="Salary min" name="salaryMin">
                        <InputNumber min={0} className="w-full" />
                    </Form.Item>
                    <Form.Item label="Salary max" name="salaryMax">
                        <InputNumber min={0} className="w-full" />
                    </Form.Item>
                    <Form.Item label="Home location" name="homeLocationLabel">
                        <Input placeholder="Bangkok, Thailand" />
                    </Form.Item>
                    <Form.Item label="Max commute minutes" name="maxCommuteMinutes">
                        <InputNumber min={0} max={240} className="w-full" />
                    </Form.Item>
                    <Form.Item label="Home latitude" name="homeLatitude">
                        <InputNumber className="w-full" />
                    </Form.Item>
                    <Form.Item label="Home longitude" name="homeLongitude">
                        <InputNumber className="w-full" />
                    </Form.Item>
                    <Form.Item label="Weekly digest" name="weeklyDigestEnabled" valuePropName="checked">
                        <Switch checkedChildren="On" unCheckedChildren="Off" />
                    </Form.Item>
                </div>

                <div className="flex justify-end">
                    <Button htmlType="submit" type="primary" icon={<SaveOutlined />} loading={saving}>
                        Save Profile
                    </Button>
                </div>
            </Form>
        </div>
    );
}
