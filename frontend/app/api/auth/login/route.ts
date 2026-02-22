import { NextRequest, NextResponse } from "next/server";

const API_GATEWAY_URL = process.env.API_GATEWAY_URL ?? "http://localhost:8082";

export async function POST(request: NextRequest) {
  try {
    const payload = await request.text();
    const response = await fetch(`${API_GATEWAY_URL}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: payload,
    });

    const body = await response.text();
    const contentType = response.headers.get("content-type") ?? "application/json";
    return new NextResponse(body, {
      status: response.status,
      headers: { "content-type": contentType },
    });
  } catch {
    return NextResponse.json(
      { error: "Auth service unavailable." },
      { status: 502 },
    );
  }
}
