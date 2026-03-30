'use client';

import { TITLE } from "@/constants/Title";
import { useChangeTitle } from "@/utils/breadCrumbUtil";
import {
    EditOutlined,
    FireOutlined,
    PictureOutlined,
    RocketOutlined,
} from "@ant-design/icons";
import { Card, Col, Row, Space, Tag, Typography } from "antd";

const { Paragraph, Text, Title } = Typography;

const BLOG_BLOCKS = [
    {
        key: "draft",
        icon: <EditOutlined />,
        title: "Draft ideas",
        description: "Keep article ideas, outlines, and publishing notes in one space before wiring the real editor.",
    },
    {
        key: "media",
        icon: <PictureOutlined />,
        title: "Media ready",
        description: "The image upload endpoint is now grouped under B-Post, so future blog assets can follow the same naming.",
    },
    {
        key: "publish",
        icon: <RocketOutlined />,
        title: "Publish flow",
        description: "This page is ready to grow into a blog workspace without mixing routes with Dinner or Chat App.",
    },
];

export default function BlogPage() {
    useChangeTitle(TITLE.B_POST, "BLOG");

    return (
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-6">
            <Card className="rounded-3xl border-0 shadow-sm">
                <Space direction="vertical" size={12}>
                    <Tag bordered={false} color="processing" icon={<FireOutlined />}>
                        B-Post workspace
                    </Tag>
                    <Title level={2} className="!mb-0">
                        Blog area is ready for the next feature wave
                    </Title>
                    <Paragraph className="!mb-0 text-base">
                        We moved the blog route under <Text code>/b-post/blog</Text> so the page structure,
                        sidebar title, and backend endpoint naming all point to the same feature domain.
                    </Paragraph>
                </Space>
            </Card>

            <Row gutter={[16, 16]}>
                {BLOG_BLOCKS.map((block) => (
                    <Col xs={24} md={12} xl={8} key={block.key}>
                        <Card className="h-full rounded-3xl border-0 shadow-sm">
                            <Space direction="vertical" size={10}>
                                <div className="text-2xl text-blue-500">{block.icon}</div>
                                <Title level={4} className="!mb-0">
                                    {block.title}
                                </Title>
                                <Paragraph className="!mb-0 text-sm">
                                    {block.description}
                                </Paragraph>
                            </Space>
                        </Card>
                    </Col>
                ))}
            </Row>
        </div>
    );
}
