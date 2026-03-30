import { Button } from 'antd';
// Reusable component
export const SidebarButton = ({
  icon,
  label,
  collapsed,
  onClick,
}: {
  icon: React.ReactNode;
  label: string;
  collapsed: boolean;
  onClick?: () => void;
}) => (
  <Button
    type="text"
    block
    icon={icon}
    className={`!flex !items-center !justify-start text-gray-600 hover:text-gray-900 `}
    onClick={onClick}
  >
    {!collapsed && <span className="ml-2">{label}</span>}
  </Button>
);