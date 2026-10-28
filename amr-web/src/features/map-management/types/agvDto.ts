/** 建立 AGV 時送往 POST /api/agvs 的資料。 */
export interface CreateAgvRequest {
  agvCode: string
  agvName: string | null
  agvType: 'STANDARD' | 'TELESCOPIC'
}

/** 將 AGV 放到地圖點位時送往 PUT /api/agvs/{agvCode}/place 的資料。 */
export interface PlaceAgvRequest {
  mapCode: string
  pointCode: string
}

/** RCS 回傳的 AGV 資料；尚未放置時，地圖、點位及座標為 null。 */
export interface AgvResponse {
  agvCode: string
  agvName: string | null
  agvType: 'STANDARD' | 'TELESCOPIC'
  status: string
  currentMapCode: string | null
  currentPoint: string | null
  posX: number | null
  posY: number | null
  angle: number | null
}
