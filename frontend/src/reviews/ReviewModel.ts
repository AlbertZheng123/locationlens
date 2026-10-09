/** UUIDs and ISO-8601 Instants are represented as strings in JSON. */
export interface ReviewModel {
  reviewId: string;
  rating: number;
  text: string;
  createdAt: string;
  userId: string;
  placeId: string;
}

export type ReviewTarget =
  | { kind: 'place'; placeId: string }
  | { kind: 'user'; userId: string };
