"use client";

import React from "react";
import {
    CheckCircleOutlined,
    ClockCircleOutlined,
    LoginOutlined,
    LogoutOutlined,
    MailOutlined,
    SafetyCertificateOutlined,
} from "@ant-design/icons";
import { Avatar, Button, Divider, Popover, Space, Tag, theme as antdTheme, Typography } from "antd";
import { useTheme } from "@/context/ThemeContext";
import { useSupabaseSession } from "@/hooks/useSupabaseSession";

const { Text, Title: AntTitle } = Typography;

interface ProfilePopoverProps {
    session: ReturnType<typeof useSupabaseSession>["data"];
    supabase: ReturnType<typeof useSupabaseSession>["supabase"];
}

export default function ProfilePopover({ session, supabase }: ProfilePopoverProps) {
    const { theme: currentTheme } = useTheme();
    const { token } = antdTheme.useToken();

    const user = session?.user;
    const metadata = user?.user_metadata ?? {};
    const appMetadata = user?.app_metadata ?? {};
    const name = metadata.full_name || metadata.name || user?.email?.split("@")[0] || "User";
    const image = metadata.avatar_url || metadata.picture;
    const providers = Array.isArray(appMetadata.providers)
        ? appMetadata.providers
        : appMetadata.provider ? [appMetadata.provider] : [];
    const providerLabel = providers.length > 0
        ? providers.map((p: unknown) => String(p)).join(", ")
        : "Email";
    const initials = String(name).trim().split(/\s+/).slice(0, 2).map((part: string) => part.charAt(0).toUpperCase()).join("") || "U";

    const handleSignOut = async () => {
        await supabase.auth.signOut();
        window.location.href = "/login";
    };

    if (!user) {
        return (
            <img
                src="/default-profile.png"
                alt="Default Profile"
                className="h-12 w-12 rounded-full border-4 border-white shadow-md dark:border-gray-700"
            />
        );
    }

    const content = (
        <div className="w-72">
            <div className="flex flex-col items-center gap-3 pb-2 pt-1">
                <Avatar
                    size={80}
                    src={image}
                    style={{
                        backgroundColor: image ? undefined : token.colorPrimary,
                        color: token.colorWhite,
                        fontSize: 28,
                        border: `3px solid ${token.colorBorderSecondary}`,
                    }}
                >
                    {!image ? initials : null}
                </Avatar>
                <div className="flex flex-col items-center gap-0.5">
                    <AntTitle level={5} style={{ margin: 0 }}>{name}</AntTitle>
                    <Text className="text-text-secondary">{user.email}</Text>
                </div>
                <Space size={[6, 6]} wrap className="justify-center">
                    <Tag bordered={false} icon={<SafetyCertificateOutlined />} color={currentTheme === "dark" ? "blue" : "processing"}>
                        Authenticated
                    </Tag>
                    <Tag bordered={false} icon={<LoginOutlined />}>{providerLabel}</Tag>
                </Space>
            </div>

            <Divider style={{ margin: "12px 0" }} />

            <div className="flex flex-col gap-2.5 px-1">
                <DetailRow icon={<MailOutlined style={{ color: token.colorPrimary }} />} label="Email" value={user.email || "N/A"} />
                <DetailRow
                    icon={<CheckCircleOutlined style={{ color: token.colorSuccess }} />}
                    label="Verified"
                    value={user.email_confirmed_at ? <Tag color="success" bordered={false}>Yes</Tag> : <Tag color="warning" bordered={false}>Pending</Tag>}
                />
                <DetailRow icon={<LoginOutlined style={{ color: token.colorPrimary }} />} label="Provider" value={providerLabel} />
                <DetailRow
                    icon={<ClockCircleOutlined style={{ color: token.colorTextSecondary }} />}
                    label="Last Sign In"
                    value={user.last_sign_in_at ? new Date(user.last_sign_in_at).toLocaleString() : "N/A"}
                />
            </div>

            <Divider style={{ margin: "12px 0" }} />

            <div className="px-1 pb-1">
                <Text className="text-xs text-text-secondary">User ID</Text>
                <div className="mt-1">
                    <Text code copyable className="text-xs">{user.id}</Text>
                </div>
            </div>

            <Divider style={{ margin: "12px 0" }} />

            <Button danger type="primary" icon={<LogoutOutlined />} onClick={handleSignOut} block>
                Sign Out
            </Button>
        </div>
    );

    const avatarNode = image ? (
        <img
            src={image}
            alt="Profile"
            className="h-12 w-12 cursor-pointer rounded-full border-4 border-white shadow-md transition-opacity hover:opacity-80 dark:border-gray-700"
        />
    ) : (
        <Avatar
            size={48}
            className="cursor-pointer border-4 border-white shadow-md transition-opacity hover:opacity-80 dark:border-gray-700"
            style={{ backgroundColor: token.colorPrimary, color: token.colorWhite, fontSize: 18 }}
        >
            {initials}
        </Avatar>
    );

    return (
        <Popover content={content} trigger="click" placement="bottomRight" arrow={{ pointAtCenter: true }}>
            {avatarNode}
        </Popover>
    );
}

function DetailRow({ icon, label, value }: { icon: React.ReactNode; label: string; value: React.ReactNode }) {
    return (
        <div className="flex items-center justify-between gap-3">
            <Space size={8}>
                {icon}
                <Text className="text-text-secondary">{label}</Text>
            </Space>
            <div className="text-right">
                {typeof value === "string" ? <Text strong>{value}</Text> : value}
            </div>
        </div>
    );
}
