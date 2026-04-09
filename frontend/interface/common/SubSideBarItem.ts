import React from "react";

export interface SubSideBarItem {
    key: string | number;
    label: string;
    icon?: React.ReactNode;
    description?: string;
    badge?: number;
    disabled?: boolean;
}
