import React, { createContext, useContext, useState } from 'react';
import { BreadcrumbItemType } from 'antd/es/breadcrumb/Breadcrumb';
import { TitleDetail } from '../interface/common/TitleDetail';
import { SubSideBarConfig } from '../interface/common/SubSideBarConfig';

export type LayoutContextType = {
  breadCrumb: BreadcrumbItemType[];
  currentTitle: TitleDetail[];
  subSideBarConfig: SubSideBarConfig | null;
  setBreadCrumb: React.Dispatch<React.SetStateAction<BreadcrumbItemType[]>>;
  setCurrentTitle: React.Dispatch<React.SetStateAction<TitleDetail[]>>;
  setSubSideBarConfig: React.Dispatch<React.SetStateAction<SubSideBarConfig | null>>;
};

const LayoutContext = createContext<LayoutContextType | undefined>(undefined);

export const useLayoutContext = () => {
  const context = useContext(LayoutContext);
  if (!context)
    throw new Error('useLayoutContext must be used within LayoutProvider');
  return context;
};


export const LayoutProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [breadCrumb, setBreadCrumb] = useState<BreadcrumbItemType[]>([]);
  const [currentTitle, setCurrentTitle] = useState<TitleDetail[]>([]);
  const [subSideBarConfig, setSubSideBarConfig] = useState<SubSideBarConfig | null>(null);

  return (
    <LayoutContext.Provider
      value={{
        breadCrumb,
        setBreadCrumb,
        currentTitle,
        setCurrentTitle,
        subSideBarConfig,
        setSubSideBarConfig,
      }}
    >
      {children}
    </LayoutContext.Provider>
  );
};
