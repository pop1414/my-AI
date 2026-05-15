import { useEffect, useEffectEvent } from "react";
import { LeftOutlined, RightOutlined } from "@ant-design/icons";
import { Button, Space, Typography } from "antd";
import { useLocation, useNavigate } from "react-router-dom";
import "./PrototypeVariantSwitcher.css";

type PrototypeVariant = {
	key: string;
	label: string;
};

type PrototypeVariantSwitcherProps = {
	variants: PrototypeVariant[];
	current: string;
	paramName?: string;
};

function isEditableTarget(target: EventTarget | null): boolean {
	if (!(target instanceof HTMLElement)) {
		return false;
	}

	const tagName = target.tagName.toLowerCase();
	return (
		tagName === "input" ||
		tagName === "textarea" ||
		target.isContentEditable
	);
}

export function PrototypeVariantSwitcher({
	variants,
	current,
	paramName = "variant",
}: PrototypeVariantSwitcherProps) {
	const navigate = useNavigate();
	const location = useLocation();

	const currentIndex = Math.max(
		0,
		variants.findIndex((variant) => variant.key === current),
	);
	const currentVariant = variants[currentIndex] ?? variants[0];

	const switchVariant = useEffectEvent((direction: -1 | 1) => {
		if (variants.length === 0) {
			return;
		}

		const nextIndex =
			(currentIndex + direction + variants.length) % variants.length;
		const params = new URLSearchParams(location.search);
		params.set(paramName, variants[nextIndex]!.key);
		navigate(
			{
				pathname: location.pathname,
				search: `?${params.toString()}`,
			},
			{ replace: true },
		);
	});

	useEffect(() => {
		if (import.meta.env.PROD) {
			return undefined;
		}

		const onKeyDown = (event: KeyboardEvent) => {
			if (isEditableTarget(event.target)) {
				return;
			}

			if (event.key === "ArrowLeft") {
				event.preventDefault();
				switchVariant(-1);
			}

			if (event.key === "ArrowRight") {
				event.preventDefault();
				switchVariant(1);
			}
		};

		window.addEventListener("keydown", onKeyDown);
		return () => window.removeEventListener("keydown", onKeyDown);
	}, [switchVariant]);

	if (import.meta.env.PROD || variants.length === 0 || !currentVariant) {
		return null;
	}

	return (
		<div className="prototype-switcher" aria-label="prototype variant switcher">
			<Space size={12} align="center">
				<Button
					shape="circle"
					icon={<LeftOutlined />}
					aria-label="上一个原型方案"
					onClick={() => switchVariant(-1)}
				/>
				<div className="prototype-switcher__label">
					<Typography.Text type="secondary">
						Prototype variant
					</Typography.Text>
					<Typography.Text strong>
						{currentVariant.key.toUpperCase()} - {currentVariant.label}
					</Typography.Text>
				</div>
				<Button
					shape="circle"
					icon={<RightOutlined />}
					aria-label="下一个原型方案"
					onClick={() => switchVariant(1)}
				/>
			</Space>
		</div>
	);
}
