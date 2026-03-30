import React from "react";
import {
    CoffeeOutlined,
    FileTextOutlined,
    HomeOutlined,
    MessageOutlined,
    ReadOutlined,
    RobotOutlined,
    ShopOutlined,
    UserOutlined,
} from "@ant-design/icons";
import { TitleDetail } from "@/interface/common/TitleDetail";

export const TITLE: Record<string, TitleDetail> = {
    HOME: {
        key: "HOME",
        title: "Home",
        urlPath: "/",
        icon: <HomeOutlined />,
    },
    B_POST: {
        key: "B_POST",
        title: "B-Post",
        icon: <ReadOutlined />,
        subTitles: [
            {
                key: "BLOG",
                title: "Blog",
                urlPath: "/b-post/blog",
            },
            {
                key: "SOCIALS",
                title: "Socials",
                urlPath: "/b-post/socials",
            },
            {
                key: "MESSAGES",
                title: "Messages",
                urlPath: "/b-post/messages",
            },
        ],
    },
    DINNER: {
        key: "DINNER",
        title: "Dinner",
        icon: <CoffeeOutlined />,
        subTitles: [
            {
                key: "SUPPLIER",
                title: "Supplier",
                urlPath: "/dinner/supplier",
            },
        ],
    },
    CHAT_APP: {
        key: "CHAT_APP",
        title: "Chat App",
        icon: <RobotOutlined />,
        subTitles: [
            {
                key: "MESSAGE",
                title: "Message",
                urlPath: "/chat-app/message",
            },
            {
                key: "SOCIAL",
                title: "Social",
                urlPath: "/chat-app/social",
            }
        ],
    },
    PROFILE: {
        key: "PROFILE",
        title: "Profile",
        urlPath: "/profile",
        icon: <UserOutlined />,
    },
};
