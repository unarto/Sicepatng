import re

with open('app/src/main/res/xml/pref_settings.xml', 'r') as f:
    content = f.read()

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

for key, new_icon in replacements.items():
    # The regex looks for the android:key="<key>" and then later app:icon="..." within the same tag.
    # Because XML tags can span multiple lines, we can use a regex that matches the tag containing the key.
    
    # Simple regex replace: find the block of the tag and replace its app:icon
    pattern = r'(android:key="' + key + r'".*?app:icon=")@drawable/[^"]+(")'
    content = re.sub(pattern, r'\1' + new_icon + r'\2', content, flags=re.DOTALL)
    
    # Also handle the case where app:icon comes BEFORE android:key
    pattern2 = r'(app:icon=")@drawable/[^"]+(".*?android:key="' + key + r'")'
    content = re.sub(pattern2, r'\1' + new_icon + r'\2', content, flags=re.DOTALL)

with open('app/src/main/res/xml/pref_settings.xml', 'w') as f:
    f.write(content)

print("Replaced icons in XML.")
