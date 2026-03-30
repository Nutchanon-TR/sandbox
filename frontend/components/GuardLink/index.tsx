'use client';
import React from 'react';
import Link, { LinkProps } from 'next/link';
import { useRouter } from 'next/navigation';

interface GuardedLinkProps extends LinkProps {
  children: React.ReactNode;
  className?: string;
}

const GuardedLink: React.FC<GuardedLinkProps> = ({ href, children, className, ...rest }) => {
  const router = useRouter();

  const onClick: React.MouseEventHandler<HTMLAnchorElement> = (e) => {
    //===========================================================================
    //For check permission in future, currently just prevent default and navigate
    //===========================================================================
    e.preventDefault();
    router.push(href.toString());
  };

  return (
    <Link href={href} onClick={onClick} className={className} {...rest}>
      {children}
    </Link>
  );
};

export default GuardedLink;