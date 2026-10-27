
interface FrpcProcessStatus {
  running: boolean;
  connectionError: string | null;
  lastStartTime?: number;
}

interface BaseEntity {
  _id: string;
}

interface FrpcSystemConfiguration {
  autoConnectOnStartup: boolean;
  language: string;
}

type OpenSourceFrpcServer = BaseEntity &
  FrpcCommonConfig & {
    multiuser: boolean;
    system: FrpcSystemConfiguration;
  };

type FrpcProxy = BaseEntity &
  FrpcProxyConfig & {
    status: number; // 0: disable 1: enable
  };
