import React from "react";

export interface TitleDetail {
    title: string;
    urlPath?: string;
    description?: string;
    icon?: React.ReactNode;
    key?: string; // Used for Antd Menu Key
    subTitles?: TitleDetail[]; // For nested sub-menus
    isSubSideBar?: boolean; // Render the secondary sidebar when this route is active
}
