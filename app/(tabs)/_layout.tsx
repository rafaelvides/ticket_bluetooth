import { Tabs } from 'expo-router';
import React, { useCallback, useEffect } from 'react';

import { HapticTab } from '@/components/haptic-tab';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { Colors } from '@/constants/theme';
import { createSocketCutom } from '@/hooks/socket';
import { useColorScheme } from '@/hooks/use-color-scheme';

export default function TabLayout() {
  const colorScheme = useColorScheme();

  const socket = createSocketCutom();

  const connect = useCallback(() => {
    socket.on("connect", () => {
      console.log("first")
    });
  }, [socket]);

  const disconnect = useCallback(() => {
    socket.on("disconnect", () => {
      console.log("cn")
    });
  }, [socket]);

  useEffect(() => {
    connect();

    return () => {
      console.log("d3w")
      disconnect();
    };
  }, []);

  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: Colors[colorScheme ?? 'light'].tint,
        headerShown: false,
        tabBarButton: HapticTab,
      }}>
      <Tabs.Screen
        name="index"
        options={{
          title: 'Home',
          tabBarIcon: ({ color }) => <IconSymbol size={28} name="house.fill" color={color} />,
        }}
      />
      <Tabs.Screen
        name="explore"
        options={{
          title: 'Explore',
          tabBarIcon: ({ color }) => <IconSymbol size={28} name="paperplane.fill" color={color} />,
        }}
      />
    </Tabs>
  );
}
