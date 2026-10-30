import Link from "next/link";

interface CategoryTabsProps {
  categories: string[];
  active?: string;
}

/** 分类筛选走真实导航（服务端带 category 重新取数），因此它是链接而不是纯前端过滤。 */
export function CategoryTabs({ categories, active }: CategoryTabsProps) {
  if (categories.length === 0) {
    return null;
  }

  const items = [{ label: "全部", value: undefined }, ...categories.map((category) => ({
    label: category,
    value: category,
  }))];

  return (
    <nav aria-label="模板分类" className="flex flex-wrap gap-2">
      {items.map((item) => {
        const selected = item.value === active;
        const href = item.value ? `/?category=${encodeURIComponent(item.value)}` : "/";
        return (
          <Link
            key={item.label}
            href={href}
            aria-current={selected ? "page" : undefined}
            className={`rounded-full border px-3.5 py-1.5 text-xs transition-colors ${
              selected
                ? "border-accent-dim bg-accent/15 text-accent-strong"
                : "border-line text-ink-muted hover:border-line-strong hover:text-ink"
            }`}
          >
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}