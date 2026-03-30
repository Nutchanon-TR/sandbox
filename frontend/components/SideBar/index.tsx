"use client";
import { TITLE } from "@/constants/Title";
import { useLayoutContext } from "@/context/LayoutContext";
import { useTheme } from "@/context/ThemeContext";
import { TitleDetail } from "@/interface/common/TitleDetail";
import {
    CodeSandboxOutlined,
    MenuFoldOutlined,
    MenuUnfoldOutlined,
    MoonOutlined,
    SunOutlined
} from "@ant-design/icons";
import type { MenuProps } from "antd";
import { theme as antdTheme, Breadcrumb, Layout, Menu } from "antd"; // Import theme จาก antd
import Link from "next/link";
import { usePathname } from "next/navigation";
import React, { useEffect, useState } from "react";
import { SidebarButton } from "../common/Button";
import { LogoutOutlined } from "@ant-design/icons";
import { useSupabaseSession } from "@/hooks/useSupabaseSession";

const { Header, Content, Sider } = Layout;

type MenuItem = Required<MenuProps>["items"][number];

export default function Sidebar({ children }: { children: React.ReactNode }) {
    const { data: session, supabase } = useSupabaseSession();
    const { breadCrumb, currentTitle } = useLayoutContext();
    const [collapsed, setCollapsed] = useState(false);
    const { theme, toggleTheme } = useTheme();
    const { setCurrentTitle } = useLayoutContext();
    const { token: { colorBgContainer, borderRadiusLG } } = antdTheme.useToken();
    const pathname = usePathname();

    useEffect(() => {
        const savedCollapsed = sessionStorage.getItem("sidebar-collapsed");
        if (savedCollapsed !== null) {
            setCollapsed(JSON.parse(savedCollapsed));
        }
    }, []);

    const handleToggleCollapse = () => {
        setCollapsed((prev) => {
            const newState = !prev;
            sessionStorage.setItem("sidebar-collapsed", JSON.stringify(newState));
            return newState;
        });
    };

    const mapTitleDetailToMenuItem = (item: TitleDetail): MenuItem => {
        const labelNode = item.urlPath ? (
            <Link href={item.urlPath} onClick={() => setCurrentTitle([item])}>{item.title}</Link>
        ) : (
            item.title
        );
        return {
            key: item.key || item.urlPath || item.title,
            icon: item.icon,
            label: labelNode,
            children: item.subTitles ? item.subTitles.map(mapTitleDetailToMenuItem) : undefined,
        } as MenuItem;
    };
    const menuItems = Object.values(TITLE).map(mapTitleDetailToMenuItem);

    const getSelectedKeys = () => {
        let matchedKey = "";
        let maxMatchLength = 0;
        const findKey = (items: TitleDetail[]) => {
            if (!items) return;
            for (const item of items) {
                if (item.urlPath) {
                    const pathLower = pathname.toLowerCase();
                    const urlPathLower = item.urlPath.toLowerCase();
                    if (pathLower.startsWith(urlPathLower) && urlPathLower.length > maxMatchLength) {
                        matchedKey = item.key || item.urlPath || item.title;
                        maxMatchLength = urlPathLower.length;
                    }
                }
                if (item.subTitles) {
                    findKey(item.subTitles);
                }
            }
        };
        findKey(Object.values(TITLE));
        return matchedKey ? [matchedKey] : (menuItems[0]?.key ? [String(menuItems[0].key)] : []);
    };

    const getOpenKeys = () => {
        const openKeys: string[] = [];
        const findOpenKey = (items: TitleDetail[], parentKey?: string) => {
            for (const item of items) {
                const pathLower = pathname.toLowerCase();
                // Check if current page is within a sub-menu
                if (item.urlPath && pathLower.includes(item.urlPath.toLowerCase())) {
                    if (parentKey) openKeys.push(parentKey);
                }
                if (item.subTitles) {
                    findOpenKey(item.subTitles, item.key || item.urlPath || item.title);
                }
            }
        };
        findOpenKey(Object.values(TITLE));
        return openKeys;
    };

    if (pathname === '/login') return (<> {children} </>);

    return (
        <Layout className="h-screen">
            <Sider
                trigger={null}
                collapsible
                collapsed={collapsed}
                theme={theme === "dark" ? "dark" : "light"}
            >
                <div className="relative h-screen">
                    <Header
                        className="flex items-center justify-center relative"
                        style={{ padding: 0, background: colorBgContainer }}
                    >
                        <Link href="/">
                            <div className={`flex items-center space-x-2 font-bold text-lg transition-colors duration-300 ${theme === 'dark' ? 'text-white' : 'text-black'}`}>
                                <CodeSandboxOutlined className="text-2xl" />
                                {!collapsed && <span>SandBox</span>}
                            </div>
                        </Link>
                        <button
                            onClick={handleToggleCollapse}
                            className={`transition-all duration-300 ease-in-out transform z-50 flex items-center justify-center cursor-pointer absolute ${collapsed
                                ? `-right-5 w-10 h-10 rounded-sm text-sm shadow-md border-none`
                                : "-right-2 text-sm w-12 h-12 border-none"
                                }`}
                            style={{
                                border: 'none',
                                boxShadow: 'none',
                            }}>
                            {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                        </button>
                    </Header>

                    <div className="flex-1 overflow-y-auto">
                        <Menu
                            theme={theme === "dark" ? "dark" : "light"}
                            selectedKeys={getSelectedKeys()}
                            defaultOpenKeys={getOpenKeys()}
                            mode="inline"
                            items={menuItems}
                            onClick={() => {
                                if (collapsed) {
                                    setCollapsed(false);
                                    sessionStorage.setItem("sidebar-collapsed", JSON.stringify(false));
                                }
                            }}
                            // ปิด border ของ Menu เพื่อความเนียน
                            style={{ borderRight: 0 }}
                        />
                    </div>

                    {/* ส่วนล่าง: ปุ่มที่ต้องการให้อยู่ติดขอบล่างเสมอ */}
                    <div className="absolute inset-x-0 bottom-0 p-4">
                        <div>
                            <SidebarButton
                                onClick={toggleTheme}
                                icon={theme === "light" ? <MoonOutlined /> : <SunOutlined />}
                                collapsed={collapsed}
                                label={theme === "light" ? "Dark Mode" : "Light Mode"}
                            />
                            <SidebarButton
                                icon={<LogoutOutlined />}
                                label="Logout"
                                collapsed={collapsed}
                                onClick={async () => {
                                    await supabase.auth.signOut();
                                    window.location.href = '/login';
                                }}
                            />
                        </div>
                    </div>
                </div>
            </Sider>

            <Layout>
                <Header
                    className={`${collapsed ? 'ml-10' : 'ml-6'} shadow-sm flex items-center justify-between transition-colors duration-300`}
                    style={{ padding: '45px 0', background: colorBgContainer, borderRadius: '10px' }}
                >
                    {/* Left: Breadcrumb + Title */}
                    <div className="ml-6">
                        <Breadcrumb
                            items={breadCrumb}
                            separator="/"
                            style={{ fontSize: '15px', fontWeight: '500', padding: '0 0 7px 0' }}
                        />
                        <h2 className="text-xl font-bold uppercase m-0 leading-none">
                            {currentTitle.length > 0 ? currentTitle[currentTitle.length - 1].title : ""}
                        </h2>
                    </div>

                    {/* Right: Profile Image */}
                    <div className="mr-6">
                        {session?.user ? (
                            <div className="flex">
                                {/* <p className="content-center mr-3 text-xl font-semibold text-gray-800 dark:text-gray-100">{session.user.name}</p> */}
                                {(session.user.user_metadata?.avatar_url || session.user.user_metadata?.picture) && (
                                    <img
                                        src={session.user.user_metadata?.avatar_url || session.user.user_metadata?.picture}
                                        alt="Profile"
                                        className="w-12 h-12 rounded-full shadow-md border-4 border-white dark:border-gray-700"
                                    />
                                )}
                            </div>
                        ) : (
                            <img
                                src="/default-profile.png"
                                alt="Default Profile"
                                className="w-16 h-16 rounded-full shadow-md border-4 border-white dark:border-gray-700"
                            />
                        )}
                    </div>
                </Header>
                <Content className={`${collapsed ? 'ml-10' : 'ml-6'} my-6 flex flex-col overflow-auto`}>
                    <div
                        className="p-6 flex-1 shadow-sm transition-colors duration-300 flex flex-col"
                        style={{
                            background: colorBgContainer,
                            borderRadius: borderRadiusLG,
                        }}
                    >
                        {children}
                    </div>
                </Content>
            </Layout>
        </Layout>
    );
}
