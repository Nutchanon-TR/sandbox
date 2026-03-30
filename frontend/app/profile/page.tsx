'use client';

import { useSupabaseSession } from "@/hooks/useSupabaseSession";
import { TITLE } from "@/constants/Title";
import { useChangeTitle } from "@/utils/breadCrumbUtil";
import { useTheme } from "@/context/ThemeContext";
import {
    BgColorsOutlined,
    IdcardOutlined,
    LockOutlined,
    LoginOutlined,
    LogoutOutlined,
    MailOutlined,
    SafetyCertificateOutlined,
    UserOutlined,
} from "@ant-design/icons";
import {
    Alert,
    Avatar,
    Button,
    Card,
    Col,
    Descriptions,
    Divider,
    Flex,
    Row,
    Skeleton,
    Space,
    Statistic,
    Tag,
    Typography,
    theme,
} from "antd";

const { Paragraph, Text, Title } = Typography;

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

    const heroBackground = currentTheme === "dark"
        ? "linear-gradient(135deg, rgba(17,26,44,0.98) 0%, rgba(21,50,91,0.94) 55%, rgba(12,20,35,0.98) 100%)"
        : "linear-gradient(135deg, #f8fbff 0%, #edf5ff 48%, #dceeff 100%)";

    const panelStyle = {
        border: `1px solid ${token.colorBorderSecondary}`,
        boxShadow: token.boxShadowSecondary,
    };

    const handleSignOut = async () => {
        await supabase.auth.signOut();
        window.location.href = "/login";
    };

    const handleLogSession = () => {
        console.log("Session Data:", session);
    };

    if (status === "loading") {
        return (
            <Card style={panelStyle}>
                <Skeleton
                    active
                    avatar={{ size: 96, shape: "circle" }}
                    paragraph={{ rows: 6 }}
                    title={{ width: "35%" }}
                />
            </Card>
        );
    }

    if (!user) {
        return (
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
        );
    }

    return (
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-6">
            <Card
                bordered={false}
                style={{
                    ...panelStyle,
                    background: heroBackground,
                    overflow: "hidden",
                }}
            >
                <Flex
                    gap={24}
                    align="center"
                    wrap="wrap"
                    justify="space-between"
                >
                    <Flex gap={20} align="center" wrap="wrap">
                        <Avatar
                            size={104}
                            src={image}
                            style={{
                                backgroundColor: image ? undefined : token.colorPrimary,
                                color: token.colorWhite,
                                fontSize: 34,
                                border: `4px solid ${token.colorBgContainer}`,
                            }}
                        >
                            {!image ? initials : null}
                        </Avatar>

                        <Space direction="vertical" size={6}>
                            <Tag
                                bordered={false}
                                icon={<SafetyCertificateOutlined />}
                                color={currentTheme === "dark" ? "blue" : "processing"}
                            >
                                Authenticated session
                            </Tag>
                            <Title level={2} style={{ margin: 0, color: token.colorText }}>
                                {name}
                            </Title>
                            <Paragraph
                                style={{
                                    margin: 0,
                                    color: token.colorTextSecondary,
                                    maxWidth: 560,
                                }}
                            >
                                Your account overview is now rendered with Ant Design components and
                                inherits the same theme tokens as the rest of the app.
                            </Paragraph>
                            <Space size={[8, 8]} wrap>
                                <Tag icon={<MailOutlined />}>
                                    {user.email}
                                </Tag>
                                <Tag icon={<LoginOutlined />}>
                                    {providerLabel}
                                </Tag>
                                <Tag icon={<BgColorsOutlined />}>
                                    {currentTheme === "dark" ? "Dark theme" : "Light theme"}
                                </Tag>
                            </Space>
                        </Space>
                    </Flex>

                    <Space wrap>
                        <Button icon={<LockOutlined />} onClick={handleLogSession}>
                            Log Session
                        </Button>
                        <Button danger type="primary" icon={<LogoutOutlined />} onClick={handleSignOut}>
                            Logout
                        </Button>
                    </Space>
                </Flex>
            </Card>

            <Row gutter={[16, 16]}>
                <Col xs={24} md={8}>
                    <Card style={panelStyle}>
                        <Statistic title="Authentication" value="Active" prefix={<SafetyCertificateOutlined />} />
                    </Card>
                </Col>
                <Col xs={24} md={8}>
                    <Card style={panelStyle}>
                        <Statistic title="Provider" value={providerLabel} prefix={<LoginOutlined />} />
                    </Card>
                </Col>
                <Col xs={24} md={8}>
                    <Card style={panelStyle}>
                        <Statistic title="Theme Mode" value={currentTheme === "dark" ? "Dark" : "Light"} prefix={<BgColorsOutlined />} />
                    </Card>
                </Col>
            </Row>

            <Row gutter={[16, 16]}>
                <Col xs={24} xl={15}>
                    <Card title="Account Details" style={panelStyle}>
                        <Descriptions
                            column={{ xs: 1, sm: 1, md: 2 }}
                            labelStyle={{ color: token.colorTextSecondary, fontWeight: 600 }}
                            contentStyle={{ color: token.colorText }}
                        >
                            <Descriptions.Item label="Display Name">{name}</Descriptions.Item>
                            <Descriptions.Item label="Email">{user.email || "N/A"}</Descriptions.Item>
                            <Descriptions.Item label="User ID">
                                <Text code copyable>
                                    {user.id}
                                </Text>
                            </Descriptions.Item>
                            <Descriptions.Item label="Primary Provider">
                                {providerLabel}
                            </Descriptions.Item>
                            <Descriptions.Item label="Email Verified">
                                {user.email_confirmed_at ? (
                                    <Tag color="success">Verified</Tag>
                                ) : (
                                    <Tag color="warning">Pending</Tag>
                                )}
                            </Descriptions.Item>
                            <Descriptions.Item label="Last Sign In">
                                {user.last_sign_in_at
                                    ? new Date(user.last_sign_in_at).toLocaleString()
                                    : "N/A"}
                            </Descriptions.Item>
                        </Descriptions>
                    </Card>
                </Col>

                <Col xs={24} xl={9}>
                    <Card title="Session Summary" style={panelStyle}>
                        <Space direction="vertical" size="middle" style={{ width: "100%" }}>
                            <Flex align="center" justify="space-between">
                                <Space>
                                    <UserOutlined style={{ color: token.colorPrimary }} />
                                    <Text strong>Identity</Text>
                                </Space>
                                <Tag color="processing">Supabase</Tag>
                            </Flex>
                            <Paragraph style={{ margin: 0, color: token.colorTextSecondary }}>
                                Profile information is read directly from the current Supabase session,
                                so updates to OAuth metadata show here without extra mapping.
                            </Paragraph>
                            <Divider style={{ margin: "8px 0" }} />
                            <Space direction="vertical" size={8}>
                                <Text>
                                    <MailOutlined style={{ color: token.colorPrimary, marginRight: 8 }} />
                                    {user.email || "No email"}
                                </Text>
                                <Text>
                                    <IdcardOutlined style={{ color: token.colorPrimary, marginRight: 8 }} />
                                    {user.id}
                                </Text>
                            </Space>
                        </Space>
                    </Card>
                </Col>
            </Row>
        </div>
    );
}
