import { FirmwareVersionItem } from "@/api/firmware";

/**
 * 格式化版本显示：版本号 (内部版本: xxx) 或仅版本号
 */
export function formatVersionLabel (version: FirmwareVersionItem): string {
    const items = [];
    if (version.internalVersion) {
        items.push(version.internalVersion)
    }
    if (version.tags) {
        const tags = JSON.parse(version.tags)
        items.push(Object.values(tags))
    }
    return `${version.version} [${items.join(", ")}]`
}