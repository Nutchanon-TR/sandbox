'use client';

import { ConfigProvider as AntdConfigProvider, theme as antdTheme, type ThemeConfig } from 'antd';
import { useTheme } from './ThemeProvider';

const darkConfig: ThemeConfig = {
  algorithm: antdTheme.darkAlgorithm,
  token: {
    colorPrimary: '#3c9ae8',
    colorLink: '#69b1ff',
    colorInfo: '#3c9ae8',
    colorSuccess: '#73d13d',
    colorWarning: '#faad14',
    colorError: '#ff7875',
    colorBgBase: '#09111f',
    colorBgLayout: '#07101d',
    colorBgContainer: '#111a2c',
    colorBgElevated: '#17233a',
    colorBorder: '#1f3655',
    colorBorderSecondary: '#162947',
    colorText: '#f3f7ff',
    colorTextSecondary: '#9fb3c8',
    borderRadius: 20,
    boxShadowSecondary: '0 20px 45px rgba(0, 0, 0, 0.28)',
    fontFamily: 'Arial, Helvetica, sans-serif',
  },
  components: {
    Layout: {
      bodyBg: '#07101d',
      siderBg: '#0f1829',
      headerBg: '#111a2c',
      triggerBg: '#111a2c',
      triggerColor: '#f3f7ff',
    },
    Menu: {
      darkItemBg: '#0f1829',
      darkSubMenuItemBg: '#0c1423',
      itemBg: 'transparent',
      itemSelectedBg: '#15325b',
      itemSelectedColor: '#f3f7ff',
      itemHoverColor: '#91caff',
    },
    Card: { colorBgContainer: '#111a2c' },
    Button: {
      controlHeight: 42,
      defaultBorderColor: '#2a4365',
      defaultColor: '#f3f7ff',
    },
  },
};

const lightConfig: ThemeConfig = {
  algorithm: antdTheme.defaultAlgorithm,
  token: {
    colorPrimary: '#1677ff',
    colorLink: '#1677ff',
    colorInfo: '#1677ff',
    colorSuccess: '#389e0d',
    colorWarning: '#d48806',
    colorError: '#cf1322',
    colorBgBase: '#f8fbff',
    colorBgLayout: '#eef3fb',
    colorBgContainer: '#ffffff',
    colorBgElevated: '#ffffff',
    colorBorder: '#d8e3f0',
    colorBorderSecondary: '#e8eef5',
    colorText: '#14213d',
    colorTextSecondary: '#5f6c85',
    borderRadius: 20,
    boxShadowSecondary: '0 20px 45px rgba(15, 23, 42, 0.08)',
    fontFamily: 'Arial, Helvetica, sans-serif',
  },
  components: {
    Layout: {
      bodyBg: '#eef3fb',
      siderBg: '#ffffff',
      headerBg: '#ffffff',
      triggerBg: '#ffffff',
      triggerColor: '#14213d',
    },
    Menu: {
      itemBg: 'transparent',
      subMenuItemBg: 'transparent',
      itemSelectedBg: '#e6f4ff',
      itemSelectedColor: '#0958d9',
      itemHoverColor: '#1677ff',
    },
    Card: { colorBgContainer: '#ffffff' },
    Button: {
      controlHeight: 42,
      defaultBorderColor: '#d8e3f0',
    },
  },
};

export function ConfigProvider({ children }: { children: React.ReactNode }) {
  const { theme } = useTheme();
  return (
    <AntdConfigProvider theme={theme === 'dark' ? darkConfig : lightConfig}>
      {children}
    </AntdConfigProvider>
  );
}
