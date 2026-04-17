import { useEffect } from 'react';
import { BreadcrumbItemType } from 'antd/es/breadcrumb/Breadcrumb';
import { TitleDetail } from '../interface/common/TitleDetail';
import { useLayoutContext } from '@/providers/LayoutProvider';
import GuardedLink from '@/components/GuardLink';

export function useChangeTitle(titleArg: TitleDetail | TitleDetail[], subKey?: string) {
  const { setBreadCrumb, setCurrentTitle } = useLayoutContext();
  useEffect(() => {
    let titles: TitleDetail[] = [];
    if (Array.isArray(titleArg)) {
      titles = titleArg;
    } else {
      titles = [titleArg];
      if (subKey && titleArg.subTitles) {
        const subTitle = titleArg.subTitles.find((sub) => sub.key === subKey);
        if (subTitle) {
          titles.push(subTitle);
        }
      }
    }

    let breadCrumb: BreadcrumbItemType[] = titles.map((title, index) => {
      if (title.urlPath && index !== titles.length - 1) {
        return {
          key: title.title,
          title: <GuardedLink href={title.urlPath}>{title.title}</GuardedLink>,
        };
      } else
        return {
          key: title.title,
          title: title.title,
        };
    });
    setCurrentTitle(titles);
    setBreadCrumb(titles.length > 1 ? breadCrumb : []);
  }, [titleArg, subKey]);
}
