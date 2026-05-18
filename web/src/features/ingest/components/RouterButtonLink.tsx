import { Link, type To } from "react-router-dom";
import type { ReactNode } from "react";

interface RouterButtonLinkProps {
	children: ReactNode;
	icon?: ReactNode;
	to: To;
	tone?: "default" | "primary" | "text" | "return";
	size?: "small";
	block?: boolean;
	testId?: string;
}

export function RouterButtonLink({
	children,
	icon,
	to,
	tone = "default",
	size,
	block,
	testId,
}: RouterButtonLinkProps) {
	const className = [
		"detail-page__button-link",
		`detail-page__button-link--${tone}`,
		size ? `detail-page__button-link--${size}` : "",
		block ? "detail-page__button-link--block" : "",
	]
		.filter(Boolean)
		.join(" ");

	return (
		<Link className={className} data-testid={testId} to={to}>
			{icon}
			<span>{children}</span>
		</Link>
	);
}
