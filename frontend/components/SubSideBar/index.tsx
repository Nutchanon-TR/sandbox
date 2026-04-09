"use client";

import { Badge, Empty, Skeleton, Typography } from "antd";
import { SubSideBarItem } from "@/interface/common/SubSideBarItem";

const { Text, Title } = Typography;

interface SubSideBarProps {
    items: SubSideBarItem[];
    onSelect: (key: string | number) => void;
    selectedKey?: string | number;
    title?: string;
    loading?: boolean;
    emptyText?: string;
}

export default function SubSideBar({
    items,
    onSelect,
    selectedKey,
    title,
    loading = false,
    emptyText = "No items found",
}: SubSideBarProps) {
    return (
        <aside className="flex h-full min-h-0 w-full flex-col bg-surface/70">
            <div className="border-b border-border-main px-5 py-4">
                <Title level={5} className="!mb-1 !text-foreground">
                    {title || "Items"}
                </Title>
                <Text className="text-xs !text-text-secondary">
                    {loading ? "Loading list..." : `${items.length} item${items.length === 1 ? "" : "s"}`}
                </Text>
            </div>

            <div className="flex-1 overflow-y-auto p-3">
                {loading ? (
                    <div className="space-y-3">
                        {Array.from({ length: 6 }).map((_, index) => (
                            <div
                                key={index}
                                className="rounded-2xl border border-border-main bg-background px-4 py-3"
                            >
                                <Skeleton
                                    active
                                    title={{ width: "70%" }}
                                    paragraph={{ rows: 1, width: ["40%"] }}
                                />
                            </div>
                        ))}
                    </div>
                ) : items.length === 0 ? (
                    <div className="flex h-full items-center justify-center">
                        <Empty description={emptyText} />
                    </div>
                ) : (
                    <div className="space-y-2">
                        {items.map((item) => {
                            const isSelected = selectedKey === item.key;

                            return (
                                <button
                                    key={item.key}
                                    type="button"
                                    onClick={() => onSelect(item.key)}
                                    disabled={item.disabled}
                                    className={`flex w-full items-start gap-3 rounded-2xl border px-4 py-3 text-left transition-all ${isSelected
                                        ? "border-blue-200 bg-blue-50 shadow-sm dark:border-blue-500/50 dark:bg-blue-500/10"
                                        : "border-transparent bg-background hover:border-border-main hover:bg-muted"
                                        } ${item.disabled ? "cursor-not-allowed opacity-60" : "cursor-pointer"}`}
                                >
                                    {item.icon && (
                                        <div className="mt-0.5 text-base text-text-secondary">
                                            {item.icon}
                                        </div>
                                    )}

                                    <div className="min-w-0 flex-1">
                                        <div className="flex items-center gap-2">
                                            <Text
                                                strong
                                                className={`truncate ${isSelected ? "!text-blue-700 dark:!text-blue-300" : "!text-foreground"}`}
                                            >
                                                {item.label}
                                            </Text>
                                            {typeof item.badge === "number" && item.badge > 0 && (
                                                <Badge count={item.badge} size="small" />
                                            )}
                                        </div>
                                        {item.description && (
                                            <Text className="mt-1 block truncate text-xs !text-text-secondary">
                                                {item.description}
                                            </Text>
                                        )}
                                    </div>
                                </button>
                            );
                        })}
                    </div>
                )}
            </div>
        </aside>
    );
}
