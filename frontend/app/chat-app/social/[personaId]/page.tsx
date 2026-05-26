import { redirectToPersonaProfile } from '../redirect';

interface SocialPersonaRedirectPageProps {
    params: Promise<{ personaId: string }>;
}

export default async function SocialPersonaRedirectPage({ params }: SocialPersonaRedirectPageProps) {
    const { personaId } = await params;
    redirectToPersonaProfile(personaId);
}
