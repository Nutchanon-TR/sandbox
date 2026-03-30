"use client";

import React, { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { TITLE } from "@/constants/Title";
import { ACCESS_DENIED_MESSAGE, ACCESS_DENIED_DESCRIPTION, PAGE_NOT_FOUND_MESSAGE, PAGE_NOT_FOUND_DESCRIPTION } from "@/constants/Message";
import { TitleDetail } from "@/interface/common/TitleDetail";
import { useNotification } from "@/context/NotificationContext";
import { useLoadingContext } from "@/context/LoadingContext";
import { createSupabaseBrowser } from "@/lib/supabase/client";

export default function NavigateGuardProvider({ children }: { children: React.ReactNode }) {
    const supabase = createSupabaseBrowser();
    const [status, setStatus] = useState<"loading" | "authenticated" | "unauthenticated">("loading");
    const pathname = usePathname();
    const router = useRouter();
    const notification = useNotification();
    const { setIsLoading } = useLoadingContext();
    const [isAuthorized, setIsAuthorized] = useState(false);

    useEffect(() => {
        const checkSession = async () => {
            const { data: { session } } = await supabase.auth.getSession();
            setStatus(session ? "authenticated" : "unauthenticated");

            const { data: authListener } = supabase.auth.onAuthStateChange(
                (event, session) => {
                    setStatus(session ? "authenticated" : "unauthenticated");
                }
            );

            return () => {
                authListener.subscription.unsubscribe();
            };
        };
        checkSession();
    }, [supabase.auth]);

    useEffect(() => {
        if (status === "loading") return;

        const validPaths = getValidPaths();
        const isPathValid = validPaths.includes(pathname);
        const isAuthenticated = status === "authenticated";

        if (pathname === "/login") {
            setIsAuthorized(true);
            return;
        }

        if (!isAuthenticated) {
            notification.warning({
                message: ACCESS_DENIED_MESSAGE,
                description: ACCESS_DENIED_DESCRIPTION,
                placement: 'bottomRight'
            });
            router.replace("/login");
            return;
        }

        if (!isPathValid) {
            notification.error({
                message: PAGE_NOT_FOUND_MESSAGE,
                description: PAGE_NOT_FOUND_DESCRIPTION,
                placement: 'bottomRight'
            });
            router.replace("/login");
            return;
        }
        setIsAuthorized(true);
    }, [pathname, status, router, notification]);

    useEffect(() => {
        if (status === "loading" || !isAuthorized) {
            setIsLoading(true);
        } else {
            setIsLoading(false);
        }
    }, [status, isAuthorized, setIsLoading]);

    // Do not return children until fully authorized (the global loading screen will cover the screen)
    if (status === "loading" || !isAuthorized) return null;

    return <>{children}</>;
}

const getValidPaths = (): string[] => {
    const paths: string[] = ["/login", "/auth/callback"];

    const extractPaths = (items: TitleDetail[] | Record<string, TitleDetail>) => {
        const iterable = Array.isArray(items) ? items : Object.values(items);
        for (const item of iterable) {
            if (item.urlPath) {
                paths.push(item.urlPath);
            }
            if (item.subTitles) {
                extractPaths(item.subTitles);
            }
        }
    };

    extractPaths(TITLE);
    return paths;
};