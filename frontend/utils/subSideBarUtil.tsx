import { useEffect } from "react";
import { useLayoutContext } from "@/providers/LayoutProvider";
import { SubSideBarConfig } from "@/interface/common/SubSideBarConfig";

export function useChangeSubSideBar(config: SubSideBarConfig | null) {
    const { setSubSideBarConfig } = useLayoutContext();

    useEffect(() => {
        setSubSideBarConfig(config);

        return () => {
            setSubSideBarConfig(null);
        };
    }, [config, setSubSideBarConfig]);
}
