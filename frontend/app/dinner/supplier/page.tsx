'use client';

import { useCallback, useDeferredValue, useEffect, useRef, useState } from "react";
import { Alert, Card, Input, Select, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import { CalendarOutlined, ShopOutlined, TruckOutlined, WarningOutlined } from "@ant-design/icons";
import { TITLE } from "@/constants/Title";
import { API_SANDBOX } from "@/constants/api/ApiSandbox";
import { useLoadingContext } from "@/context/LoadingContext";
import { useNotification } from "@/context/NotificationContext";
import { useTheme } from "@/context/ThemeContext";
import { PageResponse } from "@/interface/common/PageResponse";
import { SupplierOrder } from "@/interface/sandbox/SupplierOrder";
import { fetchApi } from "@/utils/api";
import { useChangeTitle } from "@/utils/breadCrumbUtil";

const { Search } = Input;
const { Text, Title } = Typography;

const PAGE_SIZE_OPTIONS = ["5", "10", "20", "50"];
const ACTIVE_STATUSES = ["PENDING", "PROCESSING", "CONFIRMED"];

function formatDate(value?: string) {
    if (!value) {
        return "-";
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return new Intl.DateTimeFormat("en-GB", {
        day: "2-digit",
        month: "short",
        year: "numeric",
    }).format(date);
}

function getStatusColor(status?: string) {
    const normalizedStatus = status?.trim().toUpperCase();

    if (!normalizedStatus) {
        return "default";
    }

    if (["DELIVERED", "COMPLETED", "SUCCESS"].includes(normalizedStatus)) {
        return "success";
    }

    if (["PENDING", "PROCESSING", "CONFIRMED", "IN_PROGRESS"].includes(normalizedStatus)) {
        return "processing";
    }

    if (["CANCELLED", "REJECTED", "FAILED"].includes(normalizedStatus)) {
        return "error";
    }

    return "warning";
}

function isOverdue(order: SupplierOrder) {
    const deliveryTime = new Date(order.deliveryDate).getTime();
    const status = order.status?.trim().toUpperCase();

    return !Number.isNaN(deliveryTime) && deliveryTime < Date.now() && !["DELIVERED", "COMPLETED"].includes(status);
}

function getErrorMessage(error: unknown) {
    if (typeof error === "object" && error !== null && "response" in error) {
        const response = (error as { response?: { data?: { message?: unknown } } }).response;
        if (typeof response?.data?.message === "string") {
            return response.data.message;
        }
    }

    if (error instanceof Error) {
        return error.message;
    }

    return "Please try again.";
}

export default function SupplierPage() {
    const notification = useNotification();
    const { setIsLoading } = useLoadingContext();
    const { theme } = useTheme();
    const hasLoadedRef = useRef(false);
    const [orders, setOrders] = useState<SupplierOrder[]>([]);
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [totalElements, setTotalElements] = useState(0);
    const [tableLoading, setTableLoading] = useState(false);
    const [statusFilter, setStatusFilter] = useState<string>("ALL");
    const [keyword, setKeyword] = useState("");
    const deferredKeyword = useDeferredValue(keyword);

    useChangeTitle(TITLE.DINNER, "SUPPLIER");

    const fetchSupplierOrders = useCallback(async (nextPage: number, nextPageSize: number, showOverlay = false) => {
        setTableLoading(true);
        if (showOverlay) {
            setIsLoading(true);
        }

        try {
            const response = await fetchApi<PageResponse<SupplierOrder>>(API_SANDBOX.DINNER_SUPPLIER_ORDER, {
                page: nextPage,
                size: nextPageSize,
            });

            setOrders(Array.isArray(response.content) ? response.content : []);
            setTotalElements(Number(response.totalElements) || 0);
        } catch (error: unknown) {
            console.error("Failed to fetch supplier orders:", error);
            notification.error({
                message: "Unable to load supplier orders",
                description: getErrorMessage(error),
            });
        } finally {
            setTableLoading(false);
            if (showOverlay) {
                setIsLoading(false);
            }
        }
    }, [notification, setIsLoading]);

    useEffect(() => {
        void fetchSupplierOrders(page, pageSize, !hasLoadedRef.current);
        hasLoadedRef.current = true;
    }, [fetchSupplierOrders, page, pageSize]);

    const normalizedKeyword = deferredKeyword.trim().toLowerCase();
    const filteredOrders = orders.filter((order) => {
        const matchesStatus =
            statusFilter === "ALL" || order.status?.trim().toUpperCase() === statusFilter;

        if (!normalizedKeyword) {
            return matchesStatus;
        }

        const searchableText = [
            order.supplierName,
            order.contactPerson,
            order.phone,
            order.email,
            order.status,
            order.notes,
            String(order.orderId),
        ]
            .filter(Boolean)
            .join(" ")
            .toLowerCase();

        return matchesStatus && searchableText.includes(normalizedKeyword);
    });

    const uniqueStatusOptions = ["ALL", ...new Set(orders.map((order) => order.status?.trim().toUpperCase()).filter(Boolean))];
    const uniqueSuppliers = new Set(filteredOrders.map((order) => order.supplierName)).size;
    const overdueCount = filteredOrders.filter(isOverdue).length;
    const activeCount = filteredOrders.filter((order) =>
        ACTIVE_STATUSES.includes(order.status?.trim().toUpperCase())
    ).length;

    const columns: ColumnsType<SupplierOrder> = [
        {
            title: "Order",
            dataIndex: "orderId",
            key: "orderId",
            width: 110,
            render: (value: number) => <Text strong>#{value}</Text>,
        },
        {
            title: "Supplier",
            key: "supplier",
            render: (_, record) => (
                <div className="flex flex-col">
                    <Text strong>{record.supplierName}</Text>
                    <Text type="secondary">{record.contactPerson || "No contact person"}</Text>
                </div>
            ),
        },
        {
            title: "Contact",
            key: "contact",
            render: (_, record) => (
                <div className="flex flex-col">
                    <Text>{record.phone || "-"}</Text>
                    <Text type="secondary">{record.email || "-"}</Text>
                </div>
            ),
        },
        {
            title: "Order Date",
            dataIndex: "orderDate",
            key: "orderDate",
            render: (value: string) => formatDate(value),
        },
        {
            title: "Delivery",
            dataIndex: "deliveryDate",
            key: "deliveryDate",
            render: (value: string, record) => (
                <div className="flex items-center gap-2">
                    <Text>{formatDate(value)}</Text>
                    {isOverdue(record) && <Tag color="error" className="!mr-0">Overdue</Tag>}
                </div>
            ),
        },
        {
            title: "Status",
            dataIndex: "status",
            key: "status",
            width: 130,
            render: (value: string) => (
                <Tag color={getStatusColor(value)} className="rounded-full px-3 py-1 uppercase">
                    {value || "Unknown"}
                </Tag>
            ),
        },
        {
            title: "Notes",
            dataIndex: "notes",
            key: "notes",
            ellipsis: true,
            render: (value: string) => value || "-",
        },
    ];

    const handleTableChange = (pagination: TablePaginationConfig) => {
        const nextPage = pagination.current || 1;
        const nextPageSize = pagination.pageSize || pageSize;

        if (nextPageSize !== pageSize) {
            setPage(1);
            setPageSize(nextPageSize);
            return;
        }

        setPage(nextPage);
    };

    return (
        <div className={`flex h-full flex-col gap-6 ${theme === "dark" ? "dark" : ""}`}>
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <Card className="rounded-3xl border-0 shadow-sm">
                    <Statistic title="All Orders" value={totalElements} prefix={<TruckOutlined />} />
                    <Text type="secondary">Total rows from backend pagination</Text>
                </Card>
                <Card className="rounded-3xl border-0 shadow-sm">
                    <Statistic title="Visible Suppliers" value={uniqueSuppliers} prefix={<ShopOutlined />} />
                    <Text type="secondary">Calculated from current page and filters</Text>
                </Card>
                <Card className="rounded-3xl border-0 shadow-sm">
                    <Statistic title="Active Orders" value={activeCount} prefix={<CalendarOutlined />} />
                    <Text type="secondary">Pending, processing, or confirmed</Text>
                </Card>
                <Card className="rounded-3xl border-0 shadow-sm">
                    <Statistic title="Overdue" value={overdueCount} prefix={<WarningOutlined />} />
                    <Text type="secondary">Delivery date passed but not completed</Text>
                </Card>
            </div>

            <Card className="rounded-3xl border-0 shadow-sm">
                <div className="mb-5 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                    <div>
                        <Title level={4} className="!mb-1">
                            Supplier Orders
                        </Title>
                        <Text type="secondary">
                            Browse page {page} of supplier orders and filter the current result set instantly
                        </Text>
                    </div>

                    <div className="flex flex-col gap-3 md:flex-row">
                        <Search
                            allowClear
                            placeholder="Search supplier, order, contact, note"
                            value={keyword}
                            onChange={(event) => setKeyword(event.target.value)}
                            className="w-full md:w-80"
                        />
                        <Select
                            value={statusFilter}
                            onChange={setStatusFilter}
                            className="w-full md:w-52"
                            options={uniqueStatusOptions.map((value) => ({
                                label: value === "ALL" ? "All status" : value,
                                value,
                            }))}
                        />
                    </div>
                </div>

                {filteredOrders.length === 0 && !tableLoading && (
                    <Alert
                        className="mb-4"
                        type="info"
                        showIcon
                        message="No supplier orders found"
                        description="Try changing the status filter, search keyword, or refresh the data."
                    />
                )}

                <Table<SupplierOrder>
                    rowKey={(record) => `${record.id}-${record.orderId}`}
                    columns={columns}
                    dataSource={filteredOrders}
                    loading={tableLoading}
                    scroll={{ x: 1080 }}
                    pagination={{
                        current: page,
                        pageSize,
                        total: totalElements,
                        showSizeChanger: true,
                        pageSizeOptions: PAGE_SIZE_OPTIONS,
                        showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} orders`,
                    }}
                    locale={{
                        emptyText: "No orders on this page",
                    }}
                    onChange={handleTableChange}
                />
            </Card>
        </div>
    );
}
