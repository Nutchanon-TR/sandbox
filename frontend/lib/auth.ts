import NextAuth, { DefaultSession } from "next-auth";
import type { AppProviders } from "next-auth/providers";
import Facebook from "next-auth/providers/facebook";
import Google from "next-auth/providers/google";
import Twitter from "next-auth/providers/twitter";

declare module "next-auth" {
    interface Session {
        user: {
            id: string;
        } & DefaultSession["user"];
    }
}

const providers: AppProviders = [];

const googleProvider = createGoogleProvider();
if (googleProvider) {
    providers.push(googleProvider);
}

const xProvider = createXProvider();
if (xProvider) {
    providers.push(xProvider);
}

const facebookProvider = createFacebookProvider();
if (facebookProvider) {
    providers.push(facebookProvider);
}

export const { handlers, signIn, signOut, auth } = NextAuth({
    session: {
        maxAge: 2 * 60 * 60,
    },
    providers,
    callbacks: {
        async session({ session, token }) {
            if (session?.user && token?.sub) {
                session.user.id = token.sub;
            }
            return session;
        },
    },
    pages: {
        signIn: "/login",
    },
});

function createGoogleProvider() {
    if (!process.env.GOOGLE_CLIENT_ID || !process.env.GOOGLE_CLIENT_SECRET) {
        return null;
    }

    return Google({
        clientId: process.env.GOOGLE_CLIENT_ID,
        clientSecret: process.env.GOOGLE_CLIENT_SECRET,
    });
}

function createXProvider() {
    if (!process.env.X_CLIENT_ID || !process.env.X_CLIENT_SECRET) {
        return null;
    }

    return Twitter({
        clientId: process.env.X_CLIENT_ID,
        clientSecret: process.env.X_CLIENT_SECRET,
    });
}

function createFacebookProvider() {
    if (!process.env.FACEBOOK_CLIENT_ID || !process.env.FACEBOOK_CLIENT_SECRET) {
        return null;
    }

    return Facebook({
        clientId: process.env.FACEBOOK_CLIENT_ID,
        clientSecret: process.env.FACEBOOK_CLIENT_SECRET,
    });
}
