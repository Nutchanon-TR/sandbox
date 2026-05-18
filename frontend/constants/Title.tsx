import React from "react";
import {
    HomeOutlined,
    ReadOutlined,
    RobotOutlined,
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
                isSubSideBar: true,
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
                isSubSideBar: true,
            },
            {
                key: "SOCIAL",
                title: "Social",
                urlPath: "/chat-app/social",
            }
        ],
    },
};
