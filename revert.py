original_xml = """<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <PreferenceCategory android:title="Settings">
        <ListPreference
            android:defaultValue="auto"
            android:entries="@array/language_select"
            android:entryValues="@array/language_select_value"
            android:key="pref_language"
            android:summary="%s"
            android:title="@string/title_language"
            app:icon="@drawable/ic_language_24dp" />

        <Preference
            android:key="pref_theme"
            android:title="Theme"
            android:summary="Set dark mode,adjust the color"
            app:icon="@drawable/ic_palette_24dp" />

        <Preference
            android:key="pref_backup_restore"
            android:title="Backup and Recovery"
            android:summary="Sync data via WebDAV or file"
            app:icon="@drawable/ic_restore_24dp" />

        <Preference
            android:key="pref_per_app_proxy"
            android:title="AccessControl"
            android:summary="Configure application access proxy"
            app:icon="@drawable/ic_vpn_key_24dp" />

        <Preference
            android:key="pref_routing_setting"
            android:title="Routing settings"
            android:summary="Configure rule-based routing settings"
            app:icon="@drawable/ic_routing_24dp" />

        <Preference
            android:key="pref_user_asset"
            android:title="Asset files"
            android:summary="External resource related info"
            app:icon="@drawable/ic_folder_24" />
        <Preference
            android:key="pref_logcat"
            android:title="Logcat"
            android:summary="View historical running dynamic events"
            app:icon="@drawable/ic_logcat_24dp" />
        <Preference
            android:key="pref_sub_setting"
            android:title="Subscription Group Setting"
            android:summary="Configure subscription updates and groups"
            app:icon="@drawable/ic_subscriptions_24dp" />

        <!-- I'll put these advanced ones here -->
        <ListPreference
            android:defaultValue="VPN"
            android:entries="@array/mode_entries"
            android:entryValues="@array/mode_value"
            android:key="pref_mode"
            android:summary="%s"
            android:title="@string/title_mode" />

        <CheckBoxPreference
            android:key="pref_is_booted"
            android:summary="@string/summary_pref_is_booted"
            android:title="@string/title_pref_is_booted" />

        <CheckBoxPreference
            android:key="pref_auto_remove_invalid_after_test"
            android:defaultValue="false"
            android:summary="@string/summary_pref_auto_remove_invalid_after_test"
            android:title="@string/title_pref_auto_remove_invalid_after_test" />

        <CheckBoxPreference
            android:key="pref_auto_sort_after_test"
            android:defaultValue="false"
            android:summary="@string/summary_pref_auto_sort_after_test"
            android:title="@string/title_pref_auto_sort_after_test" />
            
        <Preference
            android:key="pref_check_update"
            android:title="Check for update"
            android:summary="Check for latest version"
            app:icon="@drawable/ic_check_update_24dp" />
    </PreferenceCategory>

    <PreferenceCategory android:title="Speed Test &amp; Info Settings">
        <EditTextPreference
            android:key="pref_delay_test_url"
            android:summary="@string/summary_pref_delay_test_url"
            android:title="True delay test url"
            app:icon="@drawable/ic_3d_speed" />

        <EditTextPreference
            android:key="pref_ip_api_url"
            android:summary="@string/summary_pref_ip_api_url"
            android:title="Current connection info test url"
            app:icon="@drawable/ic_info_24dp" />
    </PreferenceCategory>

    <PreferenceCategory android:title="VPN Settings">
        <CheckBoxPreference
            android:key="pref_prefer_ipv6"
            android:summary="@string/summary_pref_prefer_ipv6"
            android:title="Prefer IPv6"
            app:icon="@drawable/ic_3d_network" />

        <CheckBoxPreference
            android:key="pref_local_dns_enabled"
            android:summary="@string/summary_pref_local_dns_enabled"
            android:title="Enable local DNS"
            app:icon="@drawable/ic_3d_routing" />

        <CheckBoxPreference
            android:key="pref_fake_dns_enabled"
            android:summary="@string/summary_pref_fake_dns_enabled"
            android:title="Enable fake DNS"
            app:icon="@drawable/ic_3d_network" />

        <CheckBoxPreference
            android:key="pref_append_http_proxy"
            android:summary="@string/summary_pref_append_http_proxy"
            android:title="Append HTTP Proxy to VPN"
            app:icon="@drawable/ic_3d_network" />

        <EditTextPreference
            android:key="pref_vpn_dns"
            android:summary="@string/summary_pref_remote_dns"
            android:title="VPN DNS (only IPv4/v6)"
            app:icon="@drawable/ic_3d_network" />

        <ListPreference
            android:defaultValue="1"
            android:entries="@array/vpn_bypass_lan"
            android:entryValues="@array/vpn_bypass_lan_value"
            android:key="pref_vpn_bypass_lan"
            android:summary="%s"
            android:title="Does VPN bypass LAN"
            app:icon="@drawable/ic_3d_network" />

        <ListPreference
            android:defaultValue="0"
            android:entries="@array/vpn_interface_address"
            android:entryValues="@array/vpn_interface_address_value"
            android:key="pref_vpn_interface_address_config_index"
            android:summary="%s"
            android:title="VPN Interface Address"
            app:icon="@drawable/ic_3d_network" />

        <EditTextPreference
            android:inputType="number"
            android:key="pref_vpn_mtu"
            android:summary="1500"
            android:title="VPN MTU (default 1500)"
            app:icon="@drawable/ic_3d_network" />

        <CheckBoxPreference
            android:defaultValue="true"
            android:key="pref_use_hev_tunnel_v2"
            android:summary="@string/summary_pref_use_hev_tunnel"
            android:title="Enable Hev TUN Feature"
            app:icon="@drawable/ic_3d_network" />

        <ListPreference
            android:defaultValue="warn"
            android:entries="@array/hev_tunnel_loglevel"
            android:entryValues="@array/hev_tunnel_loglevel"
            android:key="pref_hev_tunnel_loglevel"
            android:summary="%s"
            android:title="Hev Tun Log Level"
            app:icon="@drawable/ic_3d_network" />

        <EditTextPreference
            android:key="pref_hev_tunnel_rw_timeout_v2"
            android:summary="300,60"
            android:title="Hev Tun read/write timeout (seconds)"
            app:icon="@drawable/ic_3d_network" />
    </PreferenceCategory>

    <PreferenceCategory android:title="Core Settings">

        <CheckBoxPreference
            android:defaultValue="true"
            android:key="pref_sniffing_enabled"
            android:summary="@string/summary_pref_sniffing_enabled"
            android:title="Enable Sniffing"
            app:icon="@drawable/ic_3d_network" />

        <CheckBoxPreference
            android:defaultValue="false"
            android:key="pref_route_only_enabled"
            android:summary="@string/summary_pref_route_only_enabled"
            android:title="Enable routeOnly"
            app:icon="@drawable/ic_3d_routing" />

        <CheckBoxPreference
            android:defaultValue="false"
            android:key="pref_proxy_sharing_enabled"
            android:summary="@string/summary_pref_proxy_sharing_enabled"
            android:title="Allow connections from the LAN"
            app:icon="@drawable/ic_3d_network" />

        <CheckBoxPreference
            android:defaultValue="false"
            android:key="pref_allow_insecure"
            android:summary="@string/summary_pref_allow_insecure"
            android:title="allowInsecure"
            app:icon="@drawable/ic_lock_24dp" />

        <EditTextPreference
            android:inputType="number"
            android:key="pref_socks_port"
            android:summary="10808"
            android:title="Local proxy port"
            app:icon="@drawable/ic_3d_network" />

        <EditTextPreference
            android:key="pref_remote_dns"
            android:summary="@string/summary_pref_remote_dns"
            android:title="Remote DNS (udp/tcp/https/quic) (Optional)"
            app:icon="@drawable/ic_3d_network" />

        <EditTextPreference
            android:key="pref_domestic_dns"
            android:summary="@string/summary_pref_domestic_dns"
            android:title="Domestic DNS (Optional)"
            app:icon="@drawable/ic_3d_network" />

        <EditTextPreference
            android:key="pref_dns_hosts"
            android:summary="@string/summary_pref_dns_hosts"
            android:title="DNS hosts"
            app:icon="@drawable/ic_3d_network" />

        <ListPreference
            android:defaultValue="warning"
            android:entries="@array/core_loglevel"
            android:entryValues="@array/core_loglevel"
            android:key="pref_core_loglevel"
            android:summary="%s"
            android:title="Log Level"
            app:icon="@drawable/ic_3d_network" />

        <ListPreference
            android:defaultValue="1"
            android:entries="@array/outbound_domain_resolve_method"
            android:entryValues="@array/outbound_domain_resolve_method_value"
            android:key="pref_outbound_domain_resolve_method"
            android:summary="%s"
            android:title="Outbound domain pre-resolve method"
            app:icon="@drawable/ic_3d_network" />

    </PreferenceCategory>

    <PreferenceCategory
        android:title="Mux Settings"
        app:initialExpandedChildrenCount="0">
        <CheckBoxPreference
            android:key="pref_mux_enabled"
            android:summary="@string/summary_pref_mux_enabled"
            android:title="Enable Mux"
            app:icon="@drawable/ic_3d_network" />

        <EditTextPreference
            android:inputType="number"
            android:key="pref_mux_concurrency"
            android:summary="8"
            android:title="TCP connections (range -1 to 1024)"
            app:icon="@drawable/ic_3d_network" />

        <EditTextPreference
            android:inputType="number"
            android:key="pref_mux_xudp_concurrency"
            android:summary="8"
            android:title="XUDP connections (range -1 to 1024)"
            app:icon="@drawable/ic_3d_network" />

        <ListPreference
            android:defaultValue="reject"
            android:entries="@array/mux_xudp_quic_entries"
            android:entryValues="@array/mux_xudp_quic_value"
            android:key="pref_mux_xudp_quic"
            android:summary="%s"
            android:title="Handling of QUIC in mux tunnel"
            app:icon="@drawable/ic_3d_network" />
    </PreferenceCategory>

    <PreferenceCategory
        android:title="Fragment Settings"
        app:initialExpandedChildrenCount="0">
        <CheckBoxPreference
            android:key="pref_fragment_enabled"
            android:title="Enable Fragment"
            android:summary="Enable TLS/TCP payloads fragmenting"
            app:icon="@drawable/ic_3d_network" />

        <EditTextPreference
            android:key="pref_fragment_length"
            android:summary="50-100"
            android:title="Fragment Length (min-max)"
            app:icon="@drawable/ic_3d_network" />

        <EditTextPreference
            android:key="pref_fragment_interval"
            android:summary="10-20"
            android:title="Fragment Interval (min-max)"
            app:icon="@drawable/ic_3d_network" />

        <ListPreference
            android:defaultValue="tlshello"
            android:entries="@array/fragment_packets"
            android:entryValues="@array/fragment_packets"
            android:key="pref_fragment_packets"
            android:summary="%s"
            android:title="Fragment Packets"
            app:icon="@drawable/ic_3d_network" />
    </PreferenceCategory>

    <PreferenceCategory android:title="Other">
        <Preference
            android:key="pref_disclaimer"
            android:title="Disclaimer"
            app:icon="@drawable/ic_info_24dp" />

        <Preference
            android:key="pref_about"
            android:title="About"
            android:summary="App version and info"
            app:icon="@drawable/ic_about_24dp" />
            <Preference
            android:key="pref_hev_tunnel_config"
            android:title="HEV Tunnel Config"
            android:summary="Edit HEV SOCKS5 Tunnel configuration"
            app:icon="@drawable/ic_settings_24dp" />
    </PreferenceCategory>

</PreferenceScreen>
"""

import xml.etree.ElementTree as ET
import re

replacements = {
    'pref_prefer_ipv6': '@drawable/ic_ipv6',
    'pref_local_dns_enabled': '@drawable/ic_dns_local',
    'pref_fake_dns_enabled': '@drawable/ic_public_earth',
    'pref_append_http_proxy': '@drawable/ic_http',
    'pref_vpn_dns': '@drawable/ic_dns',
    'pref_vpn_bypass_lan': '@drawable/ic_bypass_lan',
    'pref_vpn_interface_address_config_index': '@drawable/ic_device_hub',
    'pref_vpn_mtu': '@drawable/ic_settings_ethernet',
    'pref_use_hev_tunnel_v2': '@drawable/ic_tunnel',
    'pref_hev_tunnel_loglevel': '@drawable/ic_list',
    'pref_hev_tunnel_rw_timeout_v2': '@drawable/ic_timer',
    'pref_sniffing_enabled': '@drawable/ic_visibility',
    'pref_route_only_enabled': '@drawable/ic_alt_route',
    'pref_proxy_sharing_enabled': '@drawable/ic_share_24dp',
    'pref_allow_insecure': '@drawable/ic_lock_open',
    'pref_socks_port': '@drawable/ic_port',
    'pref_remote_dns': '@drawable/ic_cloud_queue',
    'pref_domestic_dns': '@drawable/ic_home',
    'pref_dns_hosts': '@drawable/ic_storage',
    'pref_core_loglevel': '@drawable/ic_bug_report',
    'pref_outbound_domain_resolve_method': '@drawable/ic_explore',
    'pref_mux_enabled': '@drawable/ic_call_split',
    'pref_mux_concurrency': '@drawable/ic_compare_arrows',
    'pref_mux_xudp_concurrency': '@drawable/ic_swap_calls',
    'pref_mux_xudp_quic': '@drawable/ic_flash_on',
    'pref_fragment_enabled': '@drawable/ic_broken_image',
    'pref_fragment_length': '@drawable/ic_straighten',
    'pref_fragment_interval': '@drawable/ic_timer',
    'pref_fragment_packets': '@drawable/ic_inventory_2',
}

# we can use simple regexes that do NOT cross tags by negating < and >
# `[^<>]*`
content = original_xml
for key, new_icon in replacements.items():
    pattern = r'(<[^>]*android:key="' + key + r'"[^>]*app:icon=")@drawable/[a-zA-Z0-9_]+("[^>]*>)'
    content, count = re.subn(pattern, r'\g<1>' + new_icon + r'\g<2>', content)
    if count == 0:
        pattern2 = r'(<[^>]*app:icon=")@drawable/[a-zA-Z0-9_]+("[^>]*android:key="' + key + r'"[^>]*>)'
        content = re.sub(pattern2, r'\g<1>' + new_icon + r'\g<2>', content)

with open('app/src/main/res/xml/pref_settings.xml', 'w') as f:
    f.write(content)
