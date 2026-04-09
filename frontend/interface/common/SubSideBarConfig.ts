import { SubSideBarItem } from "./SubSideBarItem";

export interface SubSideBarConfig {
    title?: string;
    items: SubSideBarItem[];
    selectedKey?: string | number;
    emptyText?: string;
    loading?: boolean;
    onSelect: (key: string | number) => void;
}
