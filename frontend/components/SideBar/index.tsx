"use client";

import { TITLE } from "@/constants/Title";
import { useLayoutContext } from "@/context/LayoutContext";
import { useTheme } from "@/context/ThemeContext";
import { useSupabaseSession } from "@/hooks/useSupabaseSession";
import { TitleDetail } from "@/interface/common/TitleDetail";
import {
    CodeSandboxOutlined,
    LogoutOutlined,
    MenuFoldOutlined,
    MenuUnfoldOutlined,
    MoonOutlined,
    SunOutlined,
} from "@ant-design/icons";
import type { MenuProps } from "antd";
import { theme as antdTheme, Breadcrumb, Layout, Menu } from "antd";
import Link from "next/link";
import { usePathname } from "next/navigation";
import React, { useEffect, useState } from "react";
import ProfilePopover from "../Profile";
import SubSideBar from "../SubSideBar";
import { SidebarButton } from "../common/Button";

const { Header, Content, Sider } = Layout;

type MenuItem = Required<MenuProps>["items"][number];

function findMatchedTitle(items: TitleDetail[], pathname: string): TitleDetail | null {
    let matchedItem: TitleDetail | null = null;
    let maxMatchLength = 0;

    const visit = (nodes: TitleDetail[]) => {
        for (const item of nodes) {
            if (item.urlPath) {
                const pathLower = pathname.toLowerCase();
                const urlPathLower = item.urlPath.toLowerCase();
                if (pathLower.startsWith(urlPathLower) && urlPathLower.length > maxMatchLength) {
                    matchedItem = item;
                    maxMatchLength = urlPathLower.length;
                }
            }

            if (item.subTitles) {
                visit(item.subTitles);
            }
        }
    };

    visit(items);
    return matchedItem;
}

export default function Sidebar({ children }: { children: React.ReactNode }) {
    const { data: session, supabase } = useSupabaseSession();
    const { breadCrumb, currentTitle, setCurrentTitle, subSideBarConfig } = useLayoutContext();
    const [collapsed, setCollapsed] = useState(false);
    const [openKeys, setOpenKeys] = useState<string[]>([]);
    const { theme, toggleTheme } = useTheme();
    const { token: { colorBgContainer, borderRadiusLG } } = antdTheme.useToken();
    const pathname = usePathname();

    useEffect(() => {
        const savedCollapsed = sessionStorage.getItem("sidebar-collapsed");
        if (savedCollapsed !== null) {
            setCollapsed(JSON.parse(savedCollapsed));
        }
    }, []);

    const setCollapsedState = (nextState: boolean) => {
        setCollapsed(nextState);
        sessionStorage.setItem("sidebar-collapsed", JSON.stringify(nextState));
    };

    const handleToggleCollapse = () => {
        setCollapsedState(!collapsed);
    };

    const mapTitleDetailToMenuItem = (item: TitleDetail): MenuItem => {
        const itemKey = item.key || item.urlPath || item.title;
        const labelNode = item.urlPath ? (
            <Link href={item.urlPath} onClick={() => setCurrentTitle([item])}>{item.title}</Link>
        ) : (
            item.title
        );

        return {
            key: itemKey,
            icon: item.icon,
            label: labelNode,
            children: item.subTitles ? item.subTitles.map(mapTitleDetailToMenuItem) : undefined,
            onTitleClick: item.subTitles ? () => {
                if (collapsed) {
                    setCollapsedState(false);
                    setOpenKeys([String(itemKey)]);
                }
            } : undefined,
        } as MenuItem;
    };

    const menuItems = Object.values(TITLE).map(mapTitleDetailToMenuItem);
    const activeTitle = findMatchedTitle(Object.values(TITLE), pathname);
    const shouldRenderSubMenu = activeTitle?.isSubSideBar === true && subSideBarConfig !== null;

    const getSelectedKeys = () => {
        let matchedKey = "";
        let maxMatchLength = 0;

        const findKey = (items: TitleDetail[]) => {
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

    useEffect(() => {
        setOpenKeys(getOpenKeys());
    }, [pathname]);

    if (pathname === '/login') return <>{children}</>;

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
                        className="relative flex items-center justify-center"
                        style={{ padding: 0, background: colorBgContainer }}
                    >
                        <Link href="/">
                            <div className="flex items-center space-x-2 text-lg font-bold text-foreground transition-colors duration-300">
                                <CodeSandboxOutlined className="text-2xl" />
                                {!collapsed && <span>SandBox</span>}
                            </div>
                        </Link>
                        <button
                            onClick={handleToggleCollapse}
                            className={`absolute z-50 flex cursor-pointer items-center justify-center border-none bg-transparent text-foreground transition-all duration-300 ease-in-out ${collapsed
                                ? "-right-5 h-10 w-10 rounded-sm text-sm shadow-md"
                                : "-right-2 h-12 w-12 text-sm"
                                }`}
                        >
                            {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                        </button>
                    </Header>

                    <div className="flex-1 overflow-y-auto">
                        <Menu
                            theme={theme === "dark" ? "dark" : "light"}
                            selectedKeys={getSelectedKeys()}
                            openKeys={openKeys}
                            mode="inline"
                            items={menuItems}
                            onClick={() => {
                                if (collapsed) {
                                    setCollapsedState(false);
                                }
                            }}
                            onOpenChange={(keys) => {
                                if (!collapsed) {
                                    setOpenKeys(keys.map(String));
                                }
                            }}
                            style={{ borderRight: 0 }}
                        />
                    </div>

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

            {shouldRenderSubMenu && (
                <Sider
                    width={288}
                    theme={theme === "dark" ? "dark" : "light"}
                    style={{ background: colorBgContainer, borderRight: "1px solid rgba(148, 163, 184, 0.14)" }}
                >
                    <SubSideBar
                        title={subSideBarConfig.title}
                        items={subSideBarConfig.items}
                        selectedKey={subSideBarConfig.selectedKey}
                        loading={subSideBarConfig.loading}
                        emptyText={subSideBarConfig.emptyText}
                        onSelect={subSideBarConfig.onSelect}
                    />
                </Sider>
            )}

            <Layout>
                <Header
                    className={`${collapsed ? 'ml-10' : 'ml-6'} shadow-sm flex items-center justify-between transition-colors duration-300`}
                    style={{ padding: '45px 0', background: colorBgContainer, borderRadius: '10px' }}
                >
                    <div className="ml-6">
                        <Breadcrumb
                            items={breadCrumb}
                            separator="/"
                            style={{ fontSize: '15px', fontWeight: '500', padding: '0 0 7px 0' }}
                        />
                        <h2 className="m-0 text-xl font-bold uppercase leading-none">
                            {currentTitle.length > 0 ? currentTitle[currentTitle.length - 1].title : ""}
                        </h2>
                    </div>

                    <div className="mr-6">
                        <ProfilePopover session={session} supabase={supabase} />
                    </div>
                </Header>
                <Content className={`${collapsed ? 'ml-10' : 'ml-6'} my-6 flex flex-col overflow-auto`}>
                    <div
                        className="flex flex-1 flex-col p-6 shadow-sm transition-colors duration-300"
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

