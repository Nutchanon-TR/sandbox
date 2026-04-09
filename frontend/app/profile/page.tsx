'use client';

import { useSupabaseSession } from "@/hooks/useSupabaseSession";
import { TITLE } from "@/constants/Title";
import { useChangeTitle } from "@/utils/breadCrumbUtil";
import { useTheme } from "@/context/ThemeContext";
import {
    CheckCircleOutlined,
    ClockCircleOutlined,
    LoginOutlined,
    LogoutOutlined,
    MailOutlined,
    SafetyCertificateOutlined,
} from "@ant-design/icons";
import {
    Alert,
    Avatar,
    Button,
    Card,
    Divider,
    Skeleton,
    Space,
    Tag,
    Typography,
    theme,
} from "antd";

const { Text, Title } = Typography;

export default function ProfilePage() {
    useChangeTitle(TITLE.PROFILE);

    const { data: session, status, supabase } = useSupabaseSession();
    const { theme: currentTheme } = useTheme();
    const { token } = theme.useToken();

    const user = session?.user;
    const metadata = user?.user_metadata ?? {};
    const appMetadata = user?.app_metadata ?? {};
    const name =
        metadata.full_name ||
        metadata.name ||
        user?.email?.split("@")[0] ||
        "User";
    const image = metadata.avatar_url || metadata.picture;
    const providers = Array.isArray(appMetadata.providers)
        ? appMetadata.providers
        : appMetadata.provider
            ? [appMetadata.provider]
            : [];
    const providerLabel = providers.length > 0
        ? providers.map((provider) => String(provider)).join(", ")
        : "Email";
    const initials = String(name)
        .trim()
        .split(/\s+/)
        .slice(0, 2)
        .map((part) => part.charAt(0).toUpperCase())
        .join("") || "U";

    const handleSignOut = async () => {
        await supabase.auth.signOut();
        window.location.href = "/login";
    };

    if (status === "loading") {
        return (
            <div className="mx-auto w-full max-w-md pt-12">
                <Card className="border-border-secondary bg-surface shadow-sm">
                    <Skeleton
                        active
                        avatar={{ size: 96, shape: "circle" }}
                        paragraph={{ rows: 4 }}
                        title={{ width: "50%" }}
                    />
                </Card>
            </div>
        );
    }

    if (!user) {
        return (
            <div className="mx-auto w-full max-w-md pt-12">
                <Alert
                    message="You are not logged in"
                    description="Please sign in again to view your profile details."
                    type="warning"
                    showIcon
                    action={
                        <Button type="primary" onClick={() => { window.location.href = "/login"; }}>
                            Go to Login
                        </Button>
                    }
                />
            </div>
        );
    }

    return (
        <div className="flex h-full w-full items-start justify-center p-5">
            {/* Profile Card */}
            <Card className="w-full max-w-md border-border-secondary bg-surface shadow-sm">
                <div className="flex flex-col items-center gap-4 pb-2 pt-4">
                    <Avatar
                        size={100}
                        src={image}
                        style={{
                            backgroundColor: image ? undefined : token.colorPrimary,
                            color: token.colorWhite,
                            fontSize: 34,
                            border: `3px solid ${token.colorBorderSecondary}`,
                        }}
                    >
                        {!image ? initials : null}
                    </Avatar>

                    <div className="flex flex-col items-center gap-1">
                        <Title level={3} style={{ margin: 0 }}>
                            {name}
                        </Title>
                        <Text className="text-text-secondary">{user.email}</Text>
                    </div>

                    <Space size={[6, 6]} wrap className="justify-center">
                        <Tag
                            bordered={false}
                            icon={<SafetyCertificateOutlined />}
                            color={currentTheme === "dark" ? "blue" : "processing"}
                        >
                            Authenticated
                        </Tag>
                        <Tag bordered={false} icon={<LoginOutlined />}>
                            {providerLabel}
                        </Tag>
                    </Space>
                </div>

                <Divider style={{ margin: "16px 0" }} />

                {/* Details */}
                <div className="flex flex-col gap-3 px-1">
                    <DetailRow
                        icon={<MailOutlined style={{ color: token.colorPrimary }} />}
                        label="Email"
                        value={user.email || "N/A"}
                    />
                    <DetailRow
                        icon={<CheckCircleOutlined style={{ color: token.colorSuccess }} />}
                        label="Email Verified"
                        value={
                            user.email_confirmed_at
                                ? <Tag color="success" bordered={false}>Verified</Tag>
                                : <Tag color="warning" bordered={false}>Pending</Tag>
                        }
                    />
                    <DetailRow
                        icon={<LoginOutlined style={{ color: token.colorPrimary }} />}
                        label="Provider"
                        value={providerLabel}
                    />
                    <DetailRow
                        icon={<ClockCircleOutlined style={{ color: token.colorTextSecondary }} />}
                        label="Last Sign In"
                        value={
                            user.last_sign_in_at
                                ? new Date(user.last_sign_in_at).toLocaleString()
                                : "N/A"
                        }
                    />
                </div>

                <Divider style={{ margin: "16px 0" }} />

                {/* User ID */}
                <div className="px-1">
                    <Text className="text-xs text-text-secondary">User ID</Text>
                    <div className="mt-1">
                        <Text code copyable className="text-xs">
                            {user.id}
                        </Text>
                    </div>
                </div>

                <Divider style={{ margin: "16px 0" }} />

                <Button
                    danger
                    type="primary"
                    icon={<LogoutOutlined />}
                    onClick={handleSignOut}
                    block
                    size="large"
                >
                    Sign Out
                </Button>
            </Card>
        </div>
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
